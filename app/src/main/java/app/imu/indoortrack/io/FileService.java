package app.imu.indoortrack.io;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class FileService extends Service {

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println("Service stopped");
        SensorDataWriter.closeAllFiles();
    }
}
