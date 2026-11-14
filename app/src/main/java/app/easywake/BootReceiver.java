package app.easywake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-registers alarms after a restart, app update, or clock change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Scheduler.rescheduleAll(c);
        Reminders.scheduleAll(c);
        Alerts.schedule(c);
    }
}
