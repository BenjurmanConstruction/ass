package app.easywake;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/** One alarm and everything the user chose for it. */
public class Alarm {
    public int id;
    public int hour = 7;
    public int minute = 0;
    /** Bit 0 = Sunday ... bit 6 = Saturday. 0 means "ring once". */
    public int days = 0;
    public boolean enabled = true;
    /** Index into ToneGen.NAMES, used when no music file is chosen. */
    public int tone = 0;
    /** Music file chosen by the user, or null for a built-in tone. */
    public String uri = null;
    public String soundName = null;
    public int fadeSec = 60;
    /** 0 = off, 1 = start vibrating after the fade-in, 2 = vibrate from the start. */
    public int vibrateMode = 0;
    public int snoozeMin = 10;
    public String label = "";
    /** If in the future, the alarm is snoozed until this time (epoch ms). */
    public long snoozeUntil = 0;

    public boolean usesFile() {
        return uri != null && !uri.isEmpty();
    }

    public String soundText() {
        if (usesFile()) {
            return soundName != null && !soundName.isEmpty() ? soundName : "Music file";
        }
        int t = (tone >= 0 && tone < ToneGen.NAMES.length) ? tone : 0;
        return ToneGen.NAMES[t];
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("days", days);
        o.put("enabled", enabled);
        o.put("tone", tone);
        if (uri != null) o.put("uri", uri);
        if (soundName != null) o.put("soundName", soundName);
        o.put("fadeSec", fadeSec);
        o.put("vibrateMode", vibrateMode);
        o.put("snoozeMin", snoozeMin);
        o.put("label", label == null ? "" : label);
        o.put("snoozeUntil", snoozeUntil);
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id", 0);
        a.hour = o.optInt("hour", 7);
        a.minute = o.optInt("minute", 0);
        a.days = o.optInt("days", 0);
        a.enabled = o.optBoolean("enabled", true);
        a.tone = o.optInt("tone", 0);
        a.uri = o.has("uri") ? o.optString("uri", "") : null;
        a.soundName = o.has("soundName") ? o.optString("soundName", "") : null;
        a.fadeSec = o.optInt("fadeSec", 60);
        // Older versions stored a simple on/off; treat "on" as "after the fade-in".
        a.vibrateMode = o.has("vibrateMode") ? o.optInt("vibrateMode", 0)
                : (o.optBoolean("vibrate", false) ? 1 : 0);
        a.snoozeMin = o.optInt("snoozeMin", 10);
        a.label = o.optString("label", "");
        a.snoozeUntil = o.optLong("snoozeUntil", 0);
        return a;
    }

    /** The next time this alarm should ring, strictly after {@code now}. */
    public long nextTrigger(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            if (c.getTimeInMillis() > now) {
                int bit = c.get(Calendar.DAY_OF_WEEK) - 1;
                if (days == 0 || (days & (1 << bit)) != 0) {
                    return c.getTimeInMillis();
                }
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTimeInMillis();
    }
}
