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
        refundRemoved();
    }

    /** Gives back the points for skill-tree items that no longer exist. */
    private void refundRemoved() {
        java.util.List<String> owned = MarketData.list(this, "treeOwned", "");
        if (owned.remove("m_news")) {
            MarketData.saveList(this, "treeOwned", owned);
            Scores.addPoints(this, 40);
            Stats.log(this, "Market news was removed: 40 points refunded");
        }
    }
}
