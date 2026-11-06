package app.quietvolume;

import android.provider.Settings;

/** Quick Settings tile: turns the screen dimmer on or off. */
public class DimTile extends BaseTile {

    @Override
    public void onClick() {
        if (!Settings.canDrawOverlays(this)) {
            // Permission not given yet: open the app, which asks for it.
            openApp();
            return;
        }
        Night.prefs(this).edit()
                .putBoolean(QuietService.KEY_DIM_ON, !Night.dimOn(this))
                .putInt(Night.KEY_CURRENT, -1)
                .apply();
        syncOrOpenApp();
        update();
    }

    @Override
    void update() {
        boolean on = Night.dimOn(this) && Settings.canDrawOverlays(this);
        int percent = Math.min(Night.prefs(this).getInt(QuietService.KEY_DIM, 40),
                QuietService.MAX_DIM_PERCENT);
        show(on, on ? percent + "%" : "Off");
    }
}
