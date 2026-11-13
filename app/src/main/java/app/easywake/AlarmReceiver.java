package app.easywake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android calls this at alarm time; it hands off to the ringing service. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Intent s = new Intent(c, AlarmService.class)
                .setAction(AlarmService.ACTION_START)
                .putExtra("id", in.getIntExtra("id", -1))
                .putExtra("snooze", in.getBooleanExtra("snooze", false));
        c.startForegroundService(s);
    }
}
