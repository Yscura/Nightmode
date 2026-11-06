package app.quietvolume;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Common code for the three Quick Settings tiles. */
abstract class BaseTile extends TileService {

    /** Called whenever the Quick Settings panel opens. */
    @Override
    public void onStartListening() {
        update();
    }

    abstract void update();

    /** Applies the settings; if Android won't allow that from a tile, opens the app instead. */
    void syncOrOpenApp() {
        if (!Night.sync(this)) openApp();
    }

    @SuppressWarnings("deprecation")
    void openApp() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    void show(boolean active, String subtitle) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(subtitle);
        tile.updateTile();
    }
}
