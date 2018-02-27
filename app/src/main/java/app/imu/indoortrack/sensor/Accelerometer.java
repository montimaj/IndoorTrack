package app.imu.indoortrack.sensor;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import app.imu.indoortrack.io.SensorDataWriter;

public class Accelerometer implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mAcc;
    private SensorDataWriter mWriter;

    private static final String FILE_NAME = "AccData.csv";


    public Accelerometer(Activity activity) {
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mWriter = new SensorDataWriter(FILE_NAME, activity);

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        double x = sensorEvent.values[0];
        double y = sensorEvent.values[1];
        double z = sensorEvent.values[2];
        mWriter.writeData(x, y, z);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void startSensor() {
        mSensorManager.registerListener(this, mAcc, 1000000);
    }

    public void stopSensor() {
        mSensorManager.unregisterListener(this);
    }
}
