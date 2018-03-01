package app.imu.indoortrack.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import app.imu.indoortrack.MapsActivity;
import app.imu.indoortrack.io.SensorDataWriter;

import static app.imu.indoortrack.sensor.GpsSensor.sSensorDataFilterX;
import static app.imu.indoortrack.sensor.GpsSensor.sSensorDataFilterY;
import static app.imu.indoortrack.sensor.GpsSensor.sSensorDataFilterZ;
import static app.imu.indoortrack.sensor.GpsSensor.sFilterInitialized;
import static app.imu.indoortrack.sensor.GpsSensor.sGpsX;
import static app.imu.indoortrack.sensor.GpsSensor.sGpsY;
import static app.imu.indoortrack.sensor.GpsSensor.sGpsZ;

public class Accelerometer implements SensorEventListener {

    private MapsActivity mActivity;
    private SensorManager mSensorManager;
    private Sensor mAcc;
    private SensorDataWriter mWriter1;
    private SensorDataWriter mWriter2;
    private SensorDataWriter mWriter3;
    private RealVector mCorrectedVectorX;
    private RealVector mCorrectedVectorY;
    private RealVector mCorrectedVectorZ;

    private static final String[] FILE_NAMES = new String[] { "AccData.csv", "Corrected.csv", "Dist.csv" };
    private static final int UPDATE_INTERVAL_IN_MICROSECONDS = (int) 1E+6;

    public Accelerometer(MapsActivity activity) {
        mActivity = activity;
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mWriter1 = new SensorDataWriter(FILE_NAMES[0], activity);
        mWriter2 = new SensorDataWriter(FILE_NAMES[1], activity);
        mWriter3 = new SensorDataWriter(FILE_NAMES[2], activity);
    }

    private double getEuclideanDistance(double[] point1, double[] point2) {
        return Math.sqrt(Math.pow(point1[0]-point2[0], 2) + Math.pow(point1[1] - point2[1], 2));
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        double accX = sensorEvent.values[0];
        double accY = sensorEvent.values[1];
        double accZ = sensorEvent.values[2];
        if (sFilterInitialized) {
            performCorrections(accX, accY, accZ);
            mWriter1.writeData(accX, accY, accZ);
            accX = mCorrectedVectorX.getEntry(0);
            accY = mCorrectedVectorY.getEntry(0);
            accZ = mCorrectedVectorZ.getEntry(0);
            mWriter3.writeData(getEuclideanDistance(new double[] {accX, accY}, new double[] {sGpsX, sGpsY}));
            double[] geodetic = Projection.cartesianToGeodetic(accX, accY, accZ);
            mWriter2.writeData(geodetic[0], geodetic[1], geodetic[2]);
            updateMap(geodetic[0], geodetic[1]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    private void performCorrections(final double accX, final double accY, final double accZ) {
        Thread tX = new Thread(new Runnable() {
            @Override
            public void run() {
                RealVector u = new ArrayRealVector(new double[] {accX});
                RealVector z = new ArrayRealVector(new double[] {sGpsX});
                mCorrectedVectorX = sSensorDataFilterX.estimate(u, z);
            }
        });

        Thread tY = new Thread(new Runnable() {
            @Override
            public void run() {
                RealVector u = new ArrayRealVector(new double[] {accY});
                RealVector z = new ArrayRealVector(new double[] {sGpsY});
                mCorrectedVectorY = sSensorDataFilterY.estimate(u, z);
            }
        });

        Thread tZ = new Thread(new Runnable() {
            @Override
            public void run() {
                RealVector u = new ArrayRealVector(new double[] {accZ});
                RealVector z = new ArrayRealVector(new double[] {sGpsZ});
                mCorrectedVectorZ = sSensorDataFilterZ.estimate(u, z);
            }
        });

        tX.start();
        tY.start();
        tZ.start();

        try {
            tX.join();
            tY.join();
            tZ.join();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    private void updateMap(final double lat, final double lon) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GoogleMap map = mActivity.getMap();
                LatLng latLng = new LatLng(lat, lon);
                if (map != null) {
                    try {
                        map.setMyLocationEnabled(true);
                    } catch (SecurityException e) { e.printStackTrace(); }
                    map.addCircle(new CircleOptions().center(latLng));
                    map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    map.animateCamera(CameraUpdateFactory.zoomTo(20));
                }
            }
        });
    }

    public void startSensor() {
        mSensorManager.registerListener(this, mAcc, UPDATE_INTERVAL_IN_MICROSECONDS);
    }

    public void stopSensor() { mSensorManager.unregisterListener(this); }
}
