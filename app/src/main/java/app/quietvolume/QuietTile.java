package app.quietvolume;

/** Quick Settings tile: turns extra-quiet volume on or off. */
public class QuietTile extends BaseTile {

    @Override
    public void onClick() {
        Night.prefs(this).edit()
                .putBoolean(QuietService.KEY_ON, !Night.volumeOn(this))
                .putInt(Night.KEY_CURRENT, -1)
                .apply();
        syncOrOpenApp();
        update();
    }

    @Override
    void update() {
        boolean on = Night.volumeOn(this);
        show(on, on ? "−" + Night.prefs(this).getInt(QuietService.KEY_DB, 15) + " dB" : "Off");
    }
}
