package app.quietvolume;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** One screen: Presets, Sound and Screen sections. */
public class MainActivity extends Activity {

    private static final int MAX_REDUCTION_DB = 50;

    private SharedPreferences prefs;
    private AudioManager audio;
    private LinearLayout root;

    private static final int PRESETS_PER_ROW = 3;

    /** Holds the preset buttons, the + button and "All off"; rebuilt on every change. */
    private LinearLayout presetArea;
    private Switch volToggle;
    private SeekBar reductionSlider;
    private TextView reductionLabel;
    private SeekBar systemSlider;
    private Switch dimToggle;
    private SeekBar dimSlider;
    private TextView dimLabel;
    private TextView adStatus;
    private Button adSetupButton;
    private TextView adRestrictedHint;

    /** True while the screen is being updated from saved settings (not by the user). */
    private boolean updatingUi;
    /** Preset to apply once "Display over other apps" has been allowed (-2 = just the dim switch). */
    private Integer pendingAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Night.prefs(this);
        audio = getSystemService(AudioManager.class);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        int pad = dp(24);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        // ===== Presets =====
        heading("Presets");
        presetArea = new LinearLayout(this);
        presetArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(presetArea);
        small("Tap to use. Press and hold to update, rename or delete it. "
                + "Tap + to save your current settings as a new preset.");

        // ===== Sound =====
        heading("Sound");

        volToggle = toggle("Extra quiet volume");
        volToggle.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(QuietService.KEY_ON, on).apply();
            changedByUser();
        });

        reductionLabel = label();
        reductionSlider = slider(MAX_REDUCTION_DB);
        reductionSlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int db, boolean fromUser) {
                updateReductionLabel(db);
                if (updatingUi) return;
                prefs.edit().putInt(QuietService.KEY_DB, db).apply();
                changedByUser();
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

        // ===== Screen =====
        heading("Screen");

        dimToggle = toggle("Extra dim screen");
        dimToggle.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            if (on && !Settings.canDrawOverlays(this)) {
                setChecked(dimToggle, false);
                askOverlayPermission(-2);
                return;
            }
            prefs.edit().putBoolean(QuietService.KEY_DIM_ON, on).apply();
            changedByUser();
        });

        dimLabel = label();
        dimSlider = slider(QuietService.MAX_DIM_PERCENT);
        dimSlider.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int percent, boolean fromUser) {
                updateDimLabel(percent);
                if (updatingUi) return;
                prefs.edit().putInt(QuietService.KEY_DIM, percent).apply();
                changedByUser();
            }
        });

        // ===== YouTube ads =====
        heading("YouTube ads");
        adStatus = label();
        adStatus.setPadding(0, 0, 0, dp(8));
        adSetupButton = new Button(this);
        adSetupButton.setAllCaps(false);
        adSetupButton.setText("Turn on in Accessibility settings");
        adSetupButton.setOnClickListener(v -> {
            Toast.makeText(this, "Find \"Night Mode – YouTube ads\" and turn it on",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        root.addView(adSetupButton);
        if (Build.VERSION.SDK_INT >= 33) {
            adRestrictedHint = new TextView(this);
            adRestrictedHint.setTextSize(13);
            adRestrictedHint.setPadding(0, dp(4), 0, dp(8));
            adRestrictedHint.setText("If the switch there is greyed out: tap here, then the ⋮ menu "
                    + "in the top corner → \"Allow restricted settings\", then try again.");
            adRestrictedHint.setOnClickListener(v -> startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
            root.addView(adRestrictedHint);
        }
        adPrefToggle("Skip ads automatically", AdService.KEY_SKIP);
        adPrefToggle("Mute sound during ads", AdService.KEY_MUTE);
        adPrefToggle("Darken screen during ads", AdService.KEY_DARK);
        small("Skippable ads end as soon as YouTube shows the Skip button. "
                + "Ads that can't be skipped are muted and darkened instead.");

        // ===== Tips =====
        TextView tip = new TextView(this);
        tip.setText("Tips:\n"
                + "• Set the normal volume to a low step (not zero), then use the extra slider.\n"
                + "• Set the tablet's own brightness to its lowest, then use the dim slider.\n"
                + "• Add the Quick Settings tiles: swipe down twice, tap the pencil, and drag in "
                + "\"Quiet sound\", \"Dim screen\" and \"Night preset\".\n"
                + "• For quick sliders over any app, tap \"Adjust\" in the notification, "
                + "or press and hold one of the Night Mode tiles.\n"
                + "• If an app says a screen overlay is blocking it (e.g. when installing apps), "
                + "turn the dimmer off for a moment.");
        tip.setPadding(0, dp(32), 0, 0);
        root.addView(tip);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from the permission page
        if (pendingAfterPermission != null) {
            int pending = pendingAfterPermission;
            pendingAfterPermission = null;
            if (Settings.canDrawOverlays(this)) {
                if (pending == -2) {
                    prefs.edit().putBoolean(QuietService.KEY_DIM_ON, true).apply();
                    Night.markCustom(this);
                } else {
                    Night.apply(this, pending);
                }
                Night.sync(this);
            }
        }
        // Settings may also have changed from a tile or the notification
        refreshUi();
    }

    // ---------- Presets ----------

    private void applyPreset(int id) {
        if (Night.presetUsesDim(this, id) && !Settings.canDrawOverlays(this)) {
            askOverlayPermission(id);
            return;
        }
        Night.apply(this, id);
        Night.sync(this);
        refreshUi();
    }

    private void editPreset(int id) {
        String name = Night.name(this, id);
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"Save current settings here", "Rename", "Delete"},
                        (d, which) -> {
                    if (which == 0) {
                        Night.saveCurrent(this, id);
                        Toast.makeText(this, "Saved to " + name, Toast.LENGTH_SHORT).show();
                        refreshUi();
                    } else if (which == 1) {
                        askName("Rename preset", name, n -> {
                            Night.rename(this, id, n);
                            refreshUi();
                        });
                    } else {
                        confirmDelete(id, name);
                    }
                })
                .show();
    }

    private void newPreset() {
        askName("New preset", "Preset " + (Night.ids(this).size() + 1), n -> {
            Night.create(this, n);
            Toast.makeText(this, "Saved current settings as " + n, Toast.LENGTH_SHORT).show();
            refreshUi();
        });
    }

    private void confirmDelete(int id, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + name + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    Night.delete(this, id);
                    refreshUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askName(String title, String start, java.util.function.Consumer<String> onOk) {
        EditText input = new EditText(this);
        input.setText(start);
        input.setSingleLine();
        input.selectAll();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) onOk.accept(n);
                })
                .setNegativeButton("Cancel", null)
                .create();
        // Open the keyboard straight away
        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    // ---------- Helpers ----------

    private void changedByUser() {
        Night.markCustom(this);
        Night.sync(this);
        refreshPresetButtons();
    }

    private void askOverlayPermission(int thenApply) {
        pendingAfterPermission = thenApply;
        Toast.makeText(this, "Allow \"Display over other apps\" for Night Mode, then go back",
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    /** Shows the saved settings on screen without triggering the listeners. */
    private void refreshUi() {
        updatingUi = true;
        volToggle.setChecked(Night.volumeOn(this));
        reductionSlider.setProgress(prefs.getInt(QuietService.KEY_DB, 15));
        updateReductionLabel(reductionSlider.getProgress());
        dimToggle.setChecked(Night.dimOn(this) && Settings.canDrawOverlays(this));
        dimSlider.setProgress(Math.min(prefs.getInt(QuietService.KEY_DIM, 40),
                QuietService.MAX_DIM_PERCENT));
        updateDimLabel(dimSlider.getProgress());
        systemSlider.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        updatingUi = false;
        refreshPresetButtons();
        refreshAdStatus();
    }

    private void refreshAdStatus() {
        boolean on = AdService.isEnabled(this);
        adStatus.setText(on ? "✓ Ad helper is on. It only looks at the YouTube app."
                : "The ad helper is off. Turn it on once in Accessibility settings.");
        adSetupButton.setVisibility(on ? android.view.View.GONE : android.view.View.VISIBLE);
        if (adRestrictedHint != null) {
            adRestrictedHint.setVisibility(on ? android.view.View.GONE : android.view.View.VISIBLE);
        }
    }

    private void adPrefToggle(String text, String key) {
        Switch s = toggle(text);
        s.setChecked(prefs.getBoolean(key, true));
        s.setPadding(0, dp(8), 0, dp(8));
        s.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(key, on).apply());
    }

    /** Rebuilds the preset buttons: one per preset, then + and "All off", 3 per row. */
    private void refreshPresetButtons() {
        presetArea.removeAllViews();
        int cur = Night.anyOn(this) ? Night.current(this) : -1;

        List<Button> buttons = new ArrayList<>();
        for (int id : Night.ids(this)) {
            Button b = presetButton((id == cur ? "✓ " : "") + Night.name(this, id));
            b.setOnClickListener(v -> applyPreset(id));
            b.setOnLongClickListener(v -> {
                editPreset(id);
                return true;
            });
            buttons.add(b);
        }
        Button add = presetButton("+");
        add.setTextSize(20);
        add.setOnClickListener(v -> newPreset());
        buttons.add(add);
        Button off = presetButton("All off");
        off.setOnClickListener(v -> {
            Night.allOff(this);
            Night.sync(this);
            refreshUi();
        });
        buttons.add(off);

        LinearLayout row = null;
        for (int i = 0; i < buttons.size(); i++) {
            if (i % PRESETS_PER_ROW == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                presetArea.addView(row);
            }
            row.addView(buttons.get(i), new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        // Pad the last row so its buttons keep the same width as the others
        for (int i = buttons.size() % PRESETS_PER_ROW;
             i != 0 && i < PRESETS_PER_ROW; i++) {
            row.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        }
    }

    private Button presetButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setSingleLine();
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return b;
    }

    private void setChecked(Switch s, boolean on) {
        updatingUi = true;
        s.setChecked(on);
        updatingUi = false;
    }

    private void updateReductionLabel(int db) {
        reductionLabel.setText(db == 0
                ? "Extra reduction: none"
                : "Extra reduction: " + db + " dB quieter");
    }

    private void updateDimLabel(int percent) {
        dimLabel.setText(percent == 0
                ? "Extra darkness: none"
                : "Extra darkness: " + percent + "%");
    }

    private void heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(24);
        t.setPadding(0, root.getChildCount() == 0 ? 0 : dp(40), 0, dp(8));
        root.addView(t);
    }

    private void small(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setPadding(0, dp(4), 0, 0);
        root.addView(t);
    }

    private Switch toggle(String text) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(18);
        root.addView(s);
        return s;
    }

    private TextView label() {
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setPadding(0, dp(24), 0, dp(8));
        root.addView(t);
        return t;
    }

    private SeekBar slider(int max) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        root.addView(s);
        return s;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Lets the listeners above implement only the method they need. */
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) { }
        @Override public void onStopTrackingTouch(SeekBar s) { }
    }
}
