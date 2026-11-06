package app.quietvolume;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * YOUTUBE ADS - watches the YouTube app only. While an ad is playing it can:
 *  - tap "Skip ad" as soon as the button appears,
 *  - mute the sound, and
 *  - lay a dark layer over the screen,
 * then puts everything back when the video returns.
 *
 * Ads are recognised by the names of YouTube's on-screen parts (and the text on
 * the skip button). YouTube changes these now and then; if detection stops
 * working, watch the "NightMode" log while an ad plays (it lists what it sees).
 */
public class AdService extends AccessibilityService {

    static final String YOUTUBE = "com.google.android.youtube";
    static final String KEY_SKIP = "ad_skip";
    static final String KEY_MUTE = "ad_mute";
    static final String KEY_DARK = "ad_dark";

    /** Accessibility layers aren't limited to 80% like normal overlays. */
    private static final float AD_DARKNESS = 0.92f;
    /** The ad counts as over once no sign of it has been seen for this long. */
    private static final long END_DELAY_MS = 1500;
    private static final long POLL_MS = 500;
    private static final long MIN_SCAN_GAP_MS = 250;
    private static final long MIN_CLICK_GAP_MS = 1000;

    /** View names YouTube uses only while an ad is playing (best known, may change). */
    private static final Set<String> AD_VIEW_IDS = new HashSet<>(Arrays.asList(
            "ad_progress_text", "ad_countdown", "ad_badge", "ad_text",
            "player_learn_more_button", "visit_advertiser", "ad_cta_button"));
    /** Starts of the skip button's text / label, lower case. */
    private static final String[] SKIP_LABELS = {"skip ad", "skip", "hoppa över annons"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private AudioManager audio;
    private WindowManager windowManager;

    private boolean adActive;
    private boolean weMuted;
    private View darkView;
    private long lastAdSeen, lastScan, lastClick;

    private final Runnable scanTask = this::scan;
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            scan();
            if (adActive) handler.postDelayed(this, POLL_MS);
        }
    };

    /** True if the user has switched this service on in Accessibility settings. */
    static boolean isEnabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName me = new ComponentName(c, AdService.class);
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(me.flattenToString())
                    || s.equalsIgnoreCase(me.flattenToShortString())) return true;
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        prefs = Night.prefs(this);
        audio = getSystemService(AudioManager.class);
        windowManager = getSystemService(WindowManager.class);
        Log.i(QuietService.TAG, "YouTube ad helper connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // YouTube fires many events; scan at most every MIN_SCAN_GAP_MS, but always
        // once more after the last event so the final state isn't missed.
        long wait = Math.max(0, lastScan + MIN_SCAN_GAP_MS - SystemClock.uptimeMillis());
        handler.removeCallbacks(scanTask);
        handler.postDelayed(scanTask, wait);
    }

    private void scan() {
        long now = SystemClock.uptimeMillis();
        lastScan = now;
        boolean skip = prefs.getBoolean(KEY_SKIP, true);
        boolean mute = prefs.getBoolean(KEY_MUTE, true);
        boolean dark = prefs.getBoolean(KEY_DARK, true);
        if (!skip && !mute && !dark) {
            if (adActive) endAd();
            return;
        }

        Found found = new Found();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && YOUTUBE.contentEquals(String.valueOf(root.getPackageName()))) {
            walk(root, found, 0);
        }

        if (found.ad) {
            lastAdSeen = now;
            if (!adActive) startAd(found, mute, dark);
        } else if (adActive && now - lastAdSeen > END_DELAY_MS) {
            endAd();
        }

        if (skip && found.skipButton != null && now - lastClick > MIN_CLICK_GAP_MS) {
            lastClick = now;
            tap(found.skipButton);
        }
    }

    // ---------- Finding the ad ----------

    private static class Found {
        boolean ad;
        AccessibilityNodeInfo skipButton;
        final StringBuilder seen = new StringBuilder();
    }

    private void walk(AccessibilityNodeInfo node, Found f, int depth) {
        if (node == null || depth > 60) return;
        if (node.isVisibleToUser()) {
            String id = node.getViewIdResourceName();
            if (id != null) {
                String name = id.substring(id.indexOf('/') + 1);
                if (name.contains("skip_ad")) {
                    f.ad = true;
                    f.seen.append(name).append(' ');
                    if (f.skipButton == null) f.skipButton = node;
                } else if (AD_VIEW_IDS.contains(name)) {
                    f.ad = true;
                    f.seen.append(name).append(' ');
                }
            }
            if (isSkipLabel(node.getText()) || isSkipLabel(node.getContentDescription())) {
                f.ad = true;
                f.seen.append('"').append(node.getText() != null
                        ? node.getText() : node.getContentDescription()).append("\" ");
                if (f.skipButton == null) f.skipButton = node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), f, depth + 1);
        }
    }

    private static boolean isSkipLabel(CharSequence cs) {
        if (cs == null) return false;
        String s = cs.toString().trim().toLowerCase(Locale.ROOT);
        if (s.length() > 30) return false; // a video title, not a button
        for (String label : SKIP_LABELS) {
            if (label.equals("skip") ? s.equals("skip") || s.equals("skip ads")
                    : s.startsWith(label)) return true;
        }
        return false;
    }

    // ---------- Skipping ----------

    private void tap(AccessibilityNodeInfo node) {
        // Prefer a normal click on the button (or the nearest clickable parent)
        for (AccessibilityNodeInfo n = node; n != null; n = n.getParent()) {
            if (n.isClickable()) {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(QuietService.TAG, "Skipped ad");
                    return;
                }
                break;
            }
        }
        // Otherwise tap the middle of it on screen
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return;
        Path p = new Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 50))
                .build(), null, null);
        Log.i(QuietService.TAG, "Skipped ad (tap)");
    }

    // ---------- Muting and darkening ----------

    private void startAd(Found found, boolean mute, boolean dark) {
        adActive = true;
        Log.i(QuietService.TAG, "Ad started, saw: " + found.seen);
        if (mute && !audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            weMuted = true;
        }
        if (dark) showDark();
        handler.removeCallbacks(pollTask);
        handler.postDelayed(pollTask, POLL_MS); // keeps checking even when YouTube is quiet
    }

    private void endAd() {
        adActive = false;
        handler.removeCallbacks(pollTask);
        if (weMuted) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            weMuted = false;
        }
        hideDark();
        Log.i(QuietService.TAG, "Ad ended");
    }

    private void showDark() {
        if (darkView != null) return;
        darkView = new View(this);
        darkView.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.alpha = AD_DARKNESS;
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            windowManager.addView(darkView, lp);
        } catch (Exception e) {
            Log.w(QuietService.TAG, "Could not darken ad", e);
            darkView = null;
        }
    }

    private void hideDark() {
        if (darkView == null) return;
        try {
            windowManager.removeView(darkView);
        } catch (Exception ignored) {
        }
        darkView = null;
    }

    @Override
    public void onInterrupt() {
        if (adActive) endAd();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (adActive) endAd();
        super.onDestroy();
    }
}
