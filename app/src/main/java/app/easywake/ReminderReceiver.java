package app.easywake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android calls this when a reminder comes due. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Reminders.fire(c, in.getIntExtra("id", -1));
    }
}
