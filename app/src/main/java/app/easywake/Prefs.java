package app.easywake;

import android.content.Context;
import android.content.SharedPreferences;

/** Small app-wide settings (background choice, sleep sound choices). */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("easywake", Context.MODE_PRIVATE);
    }

    static int getInt(Context c, String key, int def) {
        return p(c).getInt(key, def);
    }

    static void putInt(Context c, String key, int value) {
        p(c).edit().putInt(key, value).apply();
    }
}
