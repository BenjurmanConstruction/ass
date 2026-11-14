package app.easywake;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Android runs this in the background every so often to check price alerts. */
public class AlertJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        if (Prefs.getInt(this, "netOff", 0) == 1) return false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Alerts.check(getApplicationContext());
                } catch (Exception ignored) {
                }
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
