package app.easywake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android calls this when a water reminder comes due. */
public class WaterReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Water.fire(c);
    }
}
