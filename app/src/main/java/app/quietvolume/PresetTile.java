package app.quietvolume;

import android.provider.Settings;

import java.util.List;

/**
 * Quick Settings tile: each tap moves to the next preset.
 * Off → first preset → second preset → … → Off
 */
public class PresetTile extends BaseTile {

    @Override
    public void onClick() {
        List<Integer> ids = Night.ids(this);
        int pos = Night.anyOn(this) ? ids.indexOf(Night.current(this)) + 1 : 0;
        if (pos >= ids.size()) {
            Night.allOff(this);
        } else {
            int next = ids.get(pos);
            if (Night.presetUsesDim(this, next) && !Settings.canDrawOverlays(this)) {
                openApp(); // dimming permission not given yet
                return;
            }
            Night.apply(this, next);
        }
        syncOrOpenApp();
        update();
    }

    @Override
    void update() {
        if (!Night.anyOn(this)) {
            show(false, "Off");
        } else {
            int cur = Night.current(this);
            show(true, cur >= 0 ? Night.name(this, cur) : "Custom");
        }
    }
}
