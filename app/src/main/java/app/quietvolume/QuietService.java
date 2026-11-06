package app.quietvolume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs in the background while either feature is on:
 *
 * QUIET VOLUME - lowers the loudness of everything the tablet plays by a chosen
 * number of decibels, below the quietest normal volume step. An audio effect
 * (DynamicsProcessing) with negative gain is attached to the global output mix
 * (session 0), and to every app that announces its own audio session.
 *
 * SCREEN DIMMER - lays a see-through black layer over the whole screen, making
 * it darker than the lowest brightness setting. Touches pass straight through.
 */
public class QuietService extends Service {

    static final String TAG = "NightMode";
    static final String PREFS = "quiet";
    static final String KEY_DB = "db";
    static final String KEY_ON = "on";          // volume feature on/off
    static final String KEY_DIM = "dim";        // darkness, percent
    static final String KEY_DIM_ON = "dim_on";  // dimmer on/off
    static final String ACTION_STOP = "app.quietvolume.STOP";

    /**
     * Android 12+ blocks touches through another app's layer that is more than
     * 80% opaque, so the darkness is capped there to keep the tablet usable.
     */
    static final int MAX_DIM_PERCENT = 80;

    private static final String CHANNEL = "quiet";
    private static final int NOTIF_ID = 1;

    /** Set while the service runs, so the screen can update it live. */
    static QuietService instance;

    private SharedPreferences prefs;

    // Volume
    private final Map<Integer, DynamicsProcessing> effects = new HashMap<>();
    private boolean volumeOn;
    private int reductionDb;

    // Dimmer
    private WindowManager windowManager;
    private View dimView;
    private WindowManager.LayoutParams dimParams;

    private final BroadcastReceiver sessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
            if (session <= 0) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                if (volumeOn) attach(session);
            } else {
                detach(session);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        windowManager = getSystemService(WindowManager.class);

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessionReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(sessionReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Night.allOff(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        refresh();
        return START_STICKY; // restart automatically if Android kills it
    }

    /** Re-reads the settings and applies them. Called by the screen on every change. */
    void refresh() {
        volumeOn = prefs.getBoolean(KEY_ON, false);
        reductionDb = prefs.getInt(KEY_DB, 15);
        boolean dimOn = prefs.getBoolean(KEY_DIM_ON, false) && Settings.canDrawOverlays(this);
        int dimPercent = Math.min(prefs.getInt(KEY_DIM, 40), MAX_DIM_PERCENT);

        if (!volumeOn && !dimOn) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        // Volume
        if (volumeOn) {
            if (!effects.containsKey(0)) attach(0); // global output mix
            applyGain();
        } else {
            releaseEffects();
        }

        // Dimmer
        if (dimOn) {
            showDim(dimPercent);
        } else {
            hideDim();
        }

        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }

    // ---------- Volume ----------

    private void attach(int session) {
        if (effects.containsKey(session)) return;
        try {
            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,            // stereo
                    false, 0,     // no pre-EQ
                    false, 0,     // no multiband compressor
                    false, 0,     // no post-EQ
                    false)        // no limiter
                    .build();
            DynamicsProcessing dp = new DynamicsProcessing(Integer.MAX_VALUE, session, config);
            dp.setInputGainAllChannelsTo(-reductionDb);
            dp.setEnabled(true);
            effects.put(session, dp);
            Log.i(TAG, "Attached to audio session " + session);
        } catch (Exception e) {
            Log.w(TAG, "Could not attach to session " + session, e);
        }
    }

    private void detach(int session) {
        DynamicsProcessing dp = effects.remove(session);
        if (dp != null) dp.release();
    }

    private void applyGain() {
        for (DynamicsProcessing dp : effects.values()) {
            try {
                dp.setInputGainAllChannelsTo(-reductionDb);
            } catch (Exception e) {
                Log.w(TAG, "Could not set gain", e);
            }
        }
    }

    private void releaseEffects() {
        for (DynamicsProcessing dp : effects.values()) dp.release();
        effects.clear();
    }

    // ---------- Dimmer ----------

    private void showDim(int percent) {
        if (dimView == null) {
            dimView = new View(this);
            dimView.setBackgroundColor(Color.BLACK);
            dimParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE      // keyboard goes to apps below
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE  // taps go to apps below
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // cover status bar too
                    PixelFormat.TRANSLUCENT);
            if (Build.VERSION.SDK_INT >= 28) {
                dimParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            dimParams.alpha = percent / 100f;
            try {
                windowManager.addView(dimView, dimParams);
            } catch (Exception e) {
                Log.w(TAG, "Could not show dimmer", e);
                dimView = null;
            }
        } else {
            dimParams.alpha = percent / 100f;
            windowManager.updateViewLayout(dimView, dimParams);
        }
    }

    private void hideDim() {
        if (dimView != null) {
            try {
                windowManager.removeView(dimView);
            } catch (Exception ignored) {
            }
            dimView = null;
        }
    }

    // ---------- Notification ----------

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Night Mode", NotificationManager.IMPORTANCE_LOW));
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, QuietService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent sliders = PendingIntent.getActivity(this, 2,
                new Intent(this, SlidersActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);

        StringBuilder text = new StringBuilder();
        if (prefs.getBoolean(KEY_ON, false)) {
            text.append("Sound −").append(prefs.getInt(KEY_DB, 15)).append(" dB");
        }
        if (prefs.getBoolean(KEY_DIM_ON, false)) {
            if (text.length() > 0) text.append("  ·  ");
            text.append("Screen dimmed ")
                    .append(Math.min(prefs.getInt(KEY_DIM, 40), MAX_DIM_PERCENT)).append("%");
        }

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_volume)
                .setContentTitle("Night Mode is on")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_dim),
                        "Adjust", sliders).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_volume),
                        "Turn all off", stop).build())
                .build();
    }

    @Override
    public void onDestroy() {
        releaseEffects();
        hideDim();
        unregisterReceiver(sessionReceiver);
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
