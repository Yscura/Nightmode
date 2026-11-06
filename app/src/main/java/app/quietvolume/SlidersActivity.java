package app.quietvolume;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Small floating panel with the darkness and volume sliders. It opens on top of
 * whatever app is running: from the "Adjust" button in the notification, or by
 * pressing and holding one of the Quick Settings tiles. Tap outside to close it.
 */
public class SlidersActivity extends Activity {

    private static final int MAX_REDUCTION_DB = 50;

    private SharedPreferences prefs;
    private AudioManager audio;
    private LinearLayout root;

    private Switch dimToggle;
    private SeekBar dimSlider;
    private TextView dimLabel;
    private Switch volToggle;
    private SeekBar reductionSlider;
    private TextView reductionLabel;
    private SeekBar systemSlider;

    private boolean updatingUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Night.prefs(this);
        audio = getSystemService(AudioManager.class);

        int pad = dp(20);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, dp(8));

        // ===== Darkness =====
        dimToggle = toggle("Extra dim screen");
        dimToggle.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            if (on && !Settings.canDrawOverlays(this)) {
                setChecked(dimToggle, false);
                Toast.makeText(this, "Open Night Mode once to allow dimming",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            prefs.edit().putBoolean(QuietService.KEY_DIM_ON, on).apply();
            changed();
        });
        dimLabel = label();
        dimSlider = slider(QuietService.MAX_DIM_PERCENT);
        dimSlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int percent, boolean fromUser) {
                updateDimLabel(percent);
                if (updatingUi) return;
                prefs.edit().putInt(QuietService.KEY_DIM, percent).apply();
                changed();
            }
        });

        // ===== Volume =====
        volToggle = toggle("Extra quiet volume");
        ((LinearLayout.LayoutParams) volToggle.getLayoutParams()).topMargin = dp(24);
        volToggle.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(QuietService.KEY_ON, on).apply();
            changed();
        });
        reductionLabel = label();
        reductionSlider = slider(MAX_REDUCTION_DB);
        reductionSlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int db, boolean fromUser) {
                updateReductionLabel(db);
                if (updatingUi) return;
                prefs.edit().putInt(QuietService.KEY_DB, db).apply();
                changed();
            }
        });

        label().setText("Normal media volume");
        systemSlider = slider(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        systemSlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
            }
        });

        // ===== Buttons =====
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);
        Button openApp = flatButton("Open app");
        openApp.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        Button done = flatButton("Done");
        done.setOnClickListener(v -> finish());
        buttons.addView(openApp, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(done, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(buttons);

        setContentView(root);
        // Make the panel use most of the screen width instead of a narrow dialog
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(480));
        getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void changed() {
        Night.markCustom(this);
        Night.sync(this);
    }

    private void refreshUi() {
        updatingUi = true;
        dimToggle.setChecked(Night.dimOn(this) && Settings.canDrawOverlays(this));
        dimSlider.setProgress(Math.min(prefs.getInt(QuietService.KEY_DIM, 40),
                QuietService.MAX_DIM_PERCENT));
        updateDimLabel(dimSlider.getProgress());
        volToggle.setChecked(Night.volumeOn(this));
        reductionSlider.setProgress(prefs.getInt(QuietService.KEY_DB, 15));
        updateReductionLabel(reductionSlider.getProgress());
        systemSlider.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        updatingUi = false;
    }

    private void setChecked(Switch s, boolean on) {
        updatingUi = true;
        s.setChecked(on);
        updatingUi = false;
    }

    private void updateDimLabel(int percent) {
        dimLabel.setText(percent == 0 ? "Darkness: none" : "Darkness: " + percent + "%");
    }

    private void updateReductionLabel(int db) {
        reductionLabel.setText(db == 0
                ? "Extra reduction: none"
                : "Extra reduction: " + db + " dB quieter");
    }

    private Switch toggle(String text) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(18);
        root.addView(s, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private TextView label() {
        TextView t = new TextView(this);
        t.setTextSize(15);
        t.setPadding(0, dp(12), 0, dp(4));
        root.addView(t);
        return t;
    }

    private SeekBar slider(int max) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        root.addView(s);
        return s;
    }

    private Button flatButton(String text) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) { }
        @Override public void onStopTrackingTouch(SeekBar s) { }
    }
}
