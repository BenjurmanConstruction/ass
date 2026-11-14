package app.easywake;

import android.app.Application;

/** Loads the chosen colors once, before any screen is built. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Ui.loadTheme(this);
    }
}
