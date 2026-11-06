package app.quietvolume;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers used by the app screen and the Quick Settings tiles. */
final class Night {

    /** Id of the preset in use, or -1 for "Custom" / everything off. */
    static final String KEY_CURRENT = "preset_current";
    /** Comma-separated preset ids, in the order they are shown. */
    private static final String KEY_IDS = "preset_ids";
    private static final String KEY_NEXT_ID = "preset_next_id";

    // The app starts with one preset, "Bedtime" (id 1). These are its starting values.
    private static final String DEF_IDS = "1";
    private static final String DEF_NAME = "Bedtime";
    private static final boolean DEF_VOL_ON = true;
    private static final int DEF_DB = 25;
    private static final boolean DEF_DIM_ON = true;
    private static final int DEF_DIM = 65;

    private Night() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(QuietService.PREFS, Context.MODE_PRIVATE);
    }

    static boolean volumeOn(Context c) {
        return prefs(c).getBoolean(QuietService.KEY_ON, false);
    }

    static boolean dimOn(Context c) {
        return prefs(c).getBoolean(QuietService.KEY_DIM_ON, false);
    }

    static boolean anyOn(Context c) {
        return volumeOn(c) || dimOn(c);
    }

    /**
     * Starts, updates or stops the background service to match the settings.
     * Returns false if Android refused to start it (can happen from a tile).
     */
    static boolean sync(Context c) {
        if (QuietService.instance != null) {
            QuietService.instance.refresh(); // stops itself if everything is off
            return true;
        }
        boolean needed = volumeOn(c) || (dimOn(c) && Settings.canDrawOverlays(c));
        if (!needed) return true;
        try {
            c.startForegroundService(new Intent(c, QuietService.class));
            return true;
        } catch (Exception e) {
            Log.w(QuietService.TAG, "Could not start service", e);
            return false;
        }
    }

    // ---------- Presets ----------

    /** All preset ids, in display order. */
    static List<Integer> ids(Context c) {
        List<Integer> ids = new ArrayList<>();
        for (String s : prefs(c).getString(KEY_IDS, DEF_IDS).split(",")) {
            if (!s.isEmpty()) ids.add(Integer.parseInt(s));
        }
        return ids;
    }

    private static void saveIds(SharedPreferences.Editor e, List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        e.putString(KEY_IDS, sb.toString());
    }

    /** Id of the preset in use, or -1 if none (or it was deleted). */
    static int current(Context c) {
        int cur = prefs(c).getInt(KEY_CURRENT, -1);
        return ids(c).contains(cur) ? cur : -1;
    }

    static void markCustom(Context c) {
        prefs(c).edit().putInt(KEY_CURRENT, -1).apply();
    }

    static String name(Context c, int id) {
        return prefs(c).getString("p" + id + "_name", DEF_NAME);
    }

    static void rename(Context c, int id, String name) {
        prefs(c).edit().putString("p" + id + "_name", name).apply();
    }

    static boolean presetUsesDim(Context c, int id) {
        return prefs(c).getBoolean("p" + id + "_dim_on", DEF_DIM_ON);
    }

    /** Creates a new preset holding the current settings. Returns its id. */
    static int create(Context c, String name) {
        SharedPreferences p = prefs(c);
        int id = p.getInt(KEY_NEXT_ID, 3); // 0-2 were used by older versions
        List<Integer> ids = ids(c);
        ids.add(id);
        SharedPreferences.Editor e = p.edit()
                .putInt(KEY_NEXT_ID, id + 1)
                .putString("p" + id + "_name", name);
        saveIds(e, ids);
        e.apply();
        saveCurrent(c, id);
        return id;
    }

    static void delete(Context c, int id) {
        List<Integer> ids = ids(c);
        ids.remove(Integer.valueOf(id));
        SharedPreferences.Editor e = prefs(c).edit();
        saveIds(e, ids);
        for (String k : new String[]{"_name", "_vol_on", "_db", "_dim_on", "_dim"}) {
            e.remove("p" + id + k);
        }
        e.apply();
    }

    /** Copies the preset into the live settings. Call sync() afterwards. */
    static void apply(Context c, int id) {
        SharedPreferences p = prefs(c);
        p.edit()
                .putBoolean(QuietService.KEY_ON, p.getBoolean("p" + id + "_vol_on", DEF_VOL_ON))
                .putInt(QuietService.KEY_DB, p.getInt("p" + id + "_db", DEF_DB))
                .putBoolean(QuietService.KEY_DIM_ON, p.getBoolean("p" + id + "_dim_on", DEF_DIM_ON))
                .putInt(QuietService.KEY_DIM, p.getInt("p" + id + "_dim", DEF_DIM))
                .putInt(KEY_CURRENT, id)
                .apply();
    }

    /** Stores the current live settings in the preset. */
    static void saveCurrent(Context c, int i) {
        SharedPreferences p = prefs(c);
        p.edit()
                .putBoolean("p" + i + "_vol_on", p.getBoolean(QuietService.KEY_ON, false))
                .putInt("p" + i + "_db", p.getInt(QuietService.KEY_DB, 15))
                .putBoolean("p" + i + "_dim_on", p.getBoolean(QuietService.KEY_DIM_ON, false))
                .putInt("p" + i + "_dim", p.getInt(QuietService.KEY_DIM, 40))
                .putInt(KEY_CURRENT, i)
                .apply();
    }

    static void allOff(Context c) {
        prefs(c).edit()
                .putBoolean(QuietService.KEY_ON, false)
                .putBoolean(QuietService.KEY_DIM_ON, false)
                .putInt(KEY_CURRENT, -1)
                .apply();
    }
}
