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

import java.util.Vector;

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
    private RealVector mCorrectedVectorX;
    private RealVector mCorrectedVectorY;
    private RealVector mCorrectedVectorZ;
    private Vector<Double> mAccVecX;
    private Vector<Double> mAccVecY;
    private Vector<Double> mAccVecZ;

    static double sAccXBias = 10d;
    static double sAccYBias = 10d;
    static double sAccZBias = 10d;

    private static final String[] FILE_NAMES = new String[] { "AccData.csv", "Corrected.csv" };
    private static final int UPDATE_INTERVAL_IN_MICROSECONDS = (int) 1E+6;
    private static final int ACC_CALIBRATION_ROUNDS = 200;

    private boolean mCalibrate;
    private int mNumReadings;

    public Accelerometer(MapsActivity activity) {
        mCalibrate = true;
        mActivity = activity;
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccVecX = new Vector<>();
        mAccVecY = new Vector<>();
        mAccVecZ = new Vector<>();
        mNumReadings = 0;
        mWriter1 = new SensorDataWriter(FILE_NAMES[0], activity);
        mWriter2 = new SensorDataWriter(FILE_NAMES[1], activity);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        double accX = sensorEvent.values[0];
        double accY = sensorEvent.values[1];
        double accZ = sensorEvent.values[2];
        if (!mCalibrate && sFilterInitialized) {
            performCorrections(accX, accY, accZ);
            mWriter1.writeData(accX, accY, accZ);
            accX = mCorrectedVectorX.getEntry(0);
            accY = mCorrectedVectorY.getEntry(0);
            accZ = mCorrectedVectorZ.getEntry(0);
            double[] geodetic = Projection.cartesianToGeodetic(accX, accY, accZ);
            mWriter2.writeData(geodetic[0], geodetic[1], geodetic[2]);
            updateMap(geodetic[0], geodetic[1]);
        } else if (mCalibrate) {
            if (mNumReadings <= ACC_CALIBRATION_ROUNDS) {
                mAccVecX.add(accX);
                mAccVecY.add(accY);
                mAccVecZ.add(accZ);
                ++mNumReadings;
                System.out.println(mNumReadings + "," + accX + "," + accY + "," + accZ);
            } else {
                SensorBias sensorBias = new SensorBias(mAccVecX, mAccVecY, mAccVecZ);
                sAccXBias = sensorBias.getBiasX();
                sAccYBias = sensorBias.getBiasY();
                sAccZBias = sensorBias.getBiasZ();
                mCalibrate = false;
            }
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

    private void updateMap(double lat, double lon) {
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

    public void startSensor() {
        mSensorManager.registerListener(this, mAcc, UPDATE_INTERVAL_IN_MICROSECONDS);
    }

    public void stopSensor() { mSensorManager.unregisterListener(this); }
}
