package app.imu.indoortrack.sensor;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import java.util.Vector;

import app.imu.indoortrack.MapsActivity;
import app.imu.indoortrack.io.SensorDataWriter;

import static app.imu.indoortrack.MapsActivity.SHARED_PREFS_NAME;
import static app.imu.indoortrack.sensor.GpsSensor.sSensorDataFilterX;
import static app.imu.indoortrack.sensor.GpsSensor.sSensorDataFilterY;
import static app.imu.indoortrack.sensor.GpsSensor.sSensorDataFilterZ;
import static app.imu.indoortrack.sensor.GpsSensor.sFilterInitialized;
import static app.imu.indoortrack.sensor.GpsSensor.sGpsX;
import static app.imu.indoortrack.sensor.GpsSensor.sGpsY;
import static app.imu.indoortrack.sensor.GpsSensor.sGpsZ;

public class InertialSensor implements SensorEventListener {

    private MapsActivity mActivity;
    private SensorManager mSensorManager;
    private SensorBias mSensorBias;
    private SensorDataWriter mWriter1;
    private SensorDataWriter mWriter2;
    private SensorDataWriter mWriter3;
    private RealVector mCorrectedVectorX;
    private RealVector mCorrectedVectorY;
    private RealVector mCorrectedVectorZ;
    private Vector<Double> mAccVecX;
    private Vector<Double> mAccVecY;
    private Vector<Double> mAccVecZ;

    private long mSensorTimeStamp;
    private double mAccX;
    private double mAccY;
    private double mAccZ;
    private float[] mAccVal = new float[3];
    private float[] mMagnetVal = new float[3];
    private float[] mRotationMatrix = new float[9];
    private float[] mOrientationMatrix = new float[3];
    private boolean mAccInitialized;
    private boolean mAccCalibrationDone;
    private int mNumReadings;

    private static final String[] FILE_NAMES = new String[] { "AccData.csv", "Corrected.csv", "Dist.csv" };
    private static final int UPDATE_INTERVAL_IN_MICROSECONDS = (int) 1E+6;
    private static final int CALIBRATION_ROUNDS = 60;

    public InertialSensor(MapsActivity activity) {
        mActivity = activity;
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        mWriter1 = new SensorDataWriter(FILE_NAMES[0], activity);
        mWriter2 = new SensorDataWriter(FILE_NAMES[1], activity);
        mWriter3 = new SensorDataWriter(FILE_NAMES[2], activity);
        SharedPreferences prefs = activity.getSharedPrefs();
        String gsonStr = prefs.getString(SHARED_PREFS_NAME, null);
        if(gsonStr == null) {
            mActivity.runOnUiThread(()-> mActivity.mProgressDialog = ProgressDialog.show(mActivity, "Calibrating", "Please wait!"));
            mAccCalibrationDone = false;
            mAccVecX = new Vector<>();
            mAccVecY = new Vector<>();
            mAccVecZ = new Vector<>();

        } else {
            mSensorBias = new Gson().fromJson(gsonStr, SensorBias.class);
            mAccCalibrationDone = true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(correctInterval(sensorEvent.timestamp)) {
            mSensorTimeStamp = sensorEvent.timestamp;
            if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                mAccVal = sensorEvent.values;
            }
            if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mMagnetVal = sensorEvent.values;
            }
            if (!mAccCalibrationDone) {
                calibrate();
            } else if (sFilterInitialized) performPostCalibrationTasks();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    private void calibrate() {
        double[] rotationAngles = getRotationAngles();
        double accX = mAccVal[0] * Math.cos(rotationAngles[0]);
        double accY = mAccVal[1] * Math.cos(rotationAngles[1]);
        double accZ = mAccVal[2] * Math.cos(rotationAngles[2]);
        System.out.println(mNumReadings + ": " + accX + "," + accY + "," + accZ);
        mAccVecX.add(accX);
        mAccVecY.add(accY);
        mAccVecZ.add(accZ);
        if (++mNumReadings == CALIBRATION_ROUNDS) {
            mActivity.runOnUiThread(() -> mActivity.mProgressDialog.dismiss());
            mAccCalibrationDone = true;
            mSensorBias = new SensorBias(mAccVecX, mAccVecY, mAccVecZ);
            String gsonStr = new Gson().toJson(mSensorBias);
            mActivity.getSharedPrefs().edit().putString(SHARED_PREFS_NAME, gsonStr).apply();
        }
    }

    private boolean correctInterval(long timestamp) {
        return (timestamp - mSensorTimeStamp) >= (long) 1E+9;
    }

    private double getEuclideanDistance(double[] point1, double[] point2) {
        return Math.sqrt(Math.pow(point1[0]-point2[0], 2) +
                Math.pow(point1[1] - point2[1], 2) + Math.pow(point1[2] - point2[2], 2));
    }

    private double[] getRotationAngles() {
        SensorManager.getRotationMatrix(mRotationMatrix, null, mAccVal, mMagnetVal);
        SensorManager.getOrientation(mRotationMatrix, mOrientationMatrix);
        double azimuth = Math.toDegrees(mOrientationMatrix[0]);
        double pitch = Math.toDegrees(mOrientationMatrix[1]);
        double roll = Math.toDegrees(mOrientationMatrix[2]);
        return new double[] {azimuth, pitch, roll};
    }

    private void performPostCalibrationTasks() {
        double[] rotationAngles = getRotationAngles();
        double accX = mAccVal[0] * Math.cos(rotationAngles[0]);
        double accY = mAccVal[1] * Math.cos(rotationAngles[1]);
        double accZ = mAccVal[2] * Math.cos(rotationAngles[2]);
        performCorrections(accX, accY, accZ);
        mWriter1.writeData(accX, accY, accZ);
        accX = mCorrectedVectorX.getEntry(0);
        accY = mCorrectedVectorY.getEntry(0);
        accZ = mCorrectedVectorZ.getEntry(0);
        if (!mAccInitialized) {
            mAccX = sGpsX;
            mAccY = sGpsY;
            mAccZ = sGpsZ;
            mAccInitialized = true;
        }
        double dist = getEuclideanDistance(new double[]{accX, accY, accZ}, new double[]{mAccX, mAccY, mAccZ});
        mWriter3.writeData(dist);
        double[] geodetic = Projection.cartesianToGeodetic(accX, accY, accZ);
        mWriter2.writeData(geodetic[0], geodetic[1], geodetic[2]);
        updateMap(geodetic[0], geodetic[1]);
        mAccX = accX;
        mAccY = accY;
        mAccZ = accZ;
    }

    private void performCorrections(final double accX, final double accY, final double accZ) {
        Thread tX = new Thread(() -> {
            RealVector u = new ArrayRealVector(new double[] {accX});
            RealVector z = new ArrayRealVector(new double[] {sGpsX});
            mCorrectedVectorX = sSensorDataFilterX.estimate(u, z);
        });

        Thread tY = new Thread(() -> {
            RealVector u = new ArrayRealVector(new double[] {accY});
            RealVector z = new ArrayRealVector(new double[] {sGpsY});
            mCorrectedVectorY = sSensorDataFilterY.estimate(u, z);
        });

        Thread tZ = new Thread(() -> {
            RealVector u = new ArrayRealVector(new double[] {accZ});
            RealVector z = new ArrayRealVector(new double[] {sGpsZ});
            mCorrectedVectorZ = sSensorDataFilterZ.estimate(u, z);
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
        mActivity.runOnUiThread(() -> {
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
        });
    }

    public void startSensors() {
        assert mSensorManager != null;
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stopSensors() { mSensorManager.unregisterListener(this); }

    public SensorBias getAccSensorBias() { return mSensorBias; }

    public boolean isAccCalibrationDone() { return mAccCalibrationDone; }
}
