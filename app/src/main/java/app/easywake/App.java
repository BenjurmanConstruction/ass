package app.easywake;

import android.app.Application;

/** Loads the chosen colors once, before any screen is built. */
public class App extends Application {
    /** The app itself, for helpers that need to read saved settings. */
    static android.content.Context ctx;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        Ui.loadTheme(this);
        MarketData.netOff = Prefs.getInt(this, "netOff", 0) == 1;
        Alerts.schedule(this);
    }
}
