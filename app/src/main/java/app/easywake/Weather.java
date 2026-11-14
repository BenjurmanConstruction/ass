package app.easywake;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Weather for one chosen city, from the free Open-Meteo service. Only the city's
 * coordinates are sent; the phone's own location is never used.
 */
final class Weather {
    /** A forecast, or the reason there isn't one. */
    static final class Report {
        boolean ok;
        String error = "";
        double temp;
        double feels;
        double wind;
        int code;
        String[] days = new String[0];
        double[] hi = new double[0];
        double[] lo = new double[0];
        int[] codes = new int[0];
        int[] rain = new int[0];
    }

    private Weather() {}

    static String city(Context c) {
        return Prefs.getString(c, "wxCity");
    }

    static boolean hasCity(Context c) {
        String s = city(c);
        return s != null && !s.isEmpty() && Prefs.getString(c, "wxLat") != null;
    }

    static boolean celsius(Context c) {
        return Prefs.getInt(c, "wxUnit", 0) == 1;
    }

    static void setCity(Context c, String label, String lat, String lon) {
        Prefs.putString(c, "wxCity", label);
        Prefs.putString(c, "wxLat", lat);
        Prefs.putString(c, "wxLon", lon);
    }

    /** Finds places by name. Each result is {label, latitude, longitude}. */
    static List<String[]> search(String name) {
        List<String[]> out = new ArrayList<>();
        try {
            String body = MarketData.http("https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(name.trim(), "UTF-8")
                    + "&count=8&language=en&format=json");
            JSONArray results = new JSONObject(body).optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                StringBuilder label = new StringBuilder(r.optString("name", ""));
                String region = r.optString("admin1", "");
                String country = r.optString("country", "");
                if (!region.isEmpty()) label.append(", ").append(region);
                if (!country.isEmpty()) label.append(", ").append(country);
                out.add(new String[]{label.toString(),
                        String.valueOf(r.optDouble("latitude", 0)),
                        String.valueOf(r.optDouble("longitude", 0))});
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    static Report fetch(Context c) {
        Report r = new Report();
        if (!hasCity(c)) {
            r.error = "no city chosen";
            return r;
        }
        try {
            boolean metric = celsius(c);
            String url = "https://api.open-meteo.com/v1/forecast?latitude="
                    + Prefs.getString(c, "wxLat") + "&longitude=" + Prefs.getString(c, "wxLon")
                    + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                    + "precipitation_probability_max"
                    + "&forecast_days=5&timezone=auto"
                    + (metric ? "" : "&temperature_unit=fahrenheit&wind_speed_unit=mph");
            JSONObject root = new JSONObject(MarketData.http(url));
            JSONObject cur = root.getJSONObject("current");
            r.temp = cur.optDouble("temperature_2m", Double.NaN);
            r.feels = cur.optDouble("apparent_temperature", r.temp);
            r.wind = cur.optDouble("wind_speed_10m", 0);
            r.code = cur.optInt("weather_code", 0);

            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                JSONArray time = daily.optJSONArray("time");
                JSONArray hi = daily.optJSONArray("temperature_2m_max");
                JSONArray lo = daily.optJSONArray("temperature_2m_min");
                JSONArray codes = daily.optJSONArray("weather_code");
                JSONArray rain = daily.optJSONArray("precipitation_probability_max");
                int n = time == null ? 0 : time.length();
                r.days = new String[n];
                r.hi = new double[n];
                r.lo = new double[n];
                r.codes = new int[n];
                r.rain = new int[n];
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.getDefault());
                for (int i = 0; i < n; i++) {
                    String label = time.optString(i, "");
                    try {
                        Date d = in.parse(label);
                        if (d != null) label = out.format(d);
                    } catch (Exception e) {
                        // Keep the raw date.
                    }
                    r.days[i] = i == 0 ? "Today" : label;
                    r.hi[i] = hi == null ? Double.NaN : hi.optDouble(i, Double.NaN);
                    r.lo[i] = lo == null ? Double.NaN : lo.optDouble(i, Double.NaN);
                    r.codes[i] = codes == null ? 0 : codes.optInt(i, 0);
                    r.rain[i] = rain == null || rain.isNull(i) ? -1 : rain.optInt(i, -1);
                }
            }
            r.ok = !Double.isNaN(r.temp);
            if (!r.ok) r.error = "no temperature returned";
        } catch (Exception e) {
            r.error = String.valueOf(e.getMessage());
        }
        return r;
    }

    static String deg(double v) {
        return Double.isNaN(v) ? "--" : Math.round(v) + "°";
    }

    /** Plain words for a standard weather code. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 65) return "Rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "Mixed";
    }

    static String icon(int code) {
        if (code == 0 || code == 1) return "☀";
        if (code == 2) return "⛅";
        if (code == 3) return "☁";
        if (code == 45 || code == 48) return "🌫";
        if (code >= 51 && code <= 67) return "🌧";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 80 && code <= 82) return "🌦";
        if (code == 85 || code == 86) return "🌨";
        if (code >= 95) return "⛈";
        return "☁";
    }
}
