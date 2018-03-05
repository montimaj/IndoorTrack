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
    private Vector<Double> mDistance = new Vector<>();

    private long mSensorTimeStamp;
    private double mAccX;
    private double mAccY;
    private double mAccZ;
    private int mNumAccVals;
    private int mNumGyroVals;
    private float[] mAvgAccVal;
    private float[] mAvgGyroVal;
    private boolean mAccInitialized;
    private boolean mAccCalibrationDone;
    private int mNumReadings;

    private static final String[] FILE_NAMES = new String[] { "AccData.csv", "Corrected.csv", "Dist.csv" };
    private static final long UPDATE_INTERVAL_IN_NANOSECONDS = (long) 1E+9;
    private static final int CALIBRATION_ROUNDS = 60;
    private static final double MIN_DISTANCE = 0.01;
    private static final double MAX_DISTANCE = 1.;

    public InertialSensor(MapsActivity activity) {
        mActivity = activity;
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        mWriter1 = new SensorDataWriter(FILE_NAMES[0], activity);
        mWriter2 = new SensorDataWriter(FILE_NAMES[1], activity);
        mWriter3 = new SensorDataWriter(FILE_NAMES[2], activity);
        initAvgFilter();
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
        boolean sensor1 = sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION;
        boolean sensor2 = sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE;
        if(correctInterval(sensorEvent.timestamp)) {
            mSensorTimeStamp = sensorEvent.timestamp;
            mAvgAccVal[0] /= mNumAccVals;
            mAvgAccVal[1] /= mNumAccVals;
            mAvgAccVal[2] /= mNumAccVals;
            mAvgGyroVal[0] /= mNumGyroVals;
            mAvgGyroVal[1] /= mNumGyroVals;
            mAvgGyroVal[2] /= mNumGyroVals;
            if (!mAccCalibrationDone) {
                calibrate();
            } else if (sFilterInitialized) {
                performPostCalibrationTasks();
            }
            initAvgFilter();
        }
        if (sensor1) {
            mNumAccVals++;
            mAvgAccVal[0] += sensorEvent.values[0];
            mAvgAccVal[1] += sensorEvent.values[1];
            mAvgAccVal[2] += sensorEvent.values[2];
        }
        if (sensor2) {
            mNumGyroVals++;
            mAvgGyroVal[0] += sensorEvent.values[0];
            mAvgGyroVal[1] += sensorEvent.values[1];
            mAvgGyroVal[2] += sensorEvent.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    private void initAvgFilter() {
        mNumAccVals = 1;
        mNumGyroVals = 1;
        mAvgAccVal = new float[3];
        mAvgGyroVal = new float[3];
    }

    private void calibrate() {
        double[] rotationAngles = getRotationAngles();
        double accX = mAvgAccVal[0] * Math.cos(rotationAngles[0]);
        double accY = mAvgAccVal[1] * Math.cos(rotationAngles[1]);
        double accZ = mAvgAccVal[2] * Math.cos(rotationAngles[2]);
        if(accX != 0. && accY != 0. && accZ != 0.) {
            mAccVecX.add(accX);
            mAccVecY.add(accY);
            mAccVecZ.add(accZ);
            System.out.println(mNumReadings + ": " + accX + "," + accY + "," + accZ);
            if (++mNumReadings > CALIBRATION_ROUNDS) {
                mActivity.runOnUiThread(() -> mActivity.mProgressDialog.dismiss());
                mAccCalibrationDone = true;
                mSensorBias = new SensorBias(mAccVecX, mAccVecY, mAccVecZ);
                String gsonStr = new Gson().toJson(mSensorBias);
                mActivity.getSharedPrefs().edit().putString(SHARED_PREFS_NAME, gsonStr).apply();
            }
        }
    }

    private boolean correctInterval(long timestamp) {
        return (timestamp - mSensorTimeStamp) >= UPDATE_INTERVAL_IN_NANOSECONDS;
    }

    private double getEuclideanDistance(double[] point1, double[] point2) {
        return Math.sqrt(Math.pow(point1[0]-point2[0], 2) +
                Math.pow(point1[1] - point2[1], 2) + Math.pow(point1[2] - point2[2], 2));
    }

    private double[] getRotationAngles() {
        double azimuth = Math.toDegrees(mAvgGyroVal[0]);
        double pitch = Math.toDegrees(mAvgGyroVal[1]);
        double roll = Math.toDegrees(mAvgGyroVal[2]);
        return new double[] {azimuth, pitch, roll};
    }

    private void performPostCalibrationTasks() {
        double[] rotationAngles = getRotationAngles();
        double accX = mAvgAccVal[0] * Math.cos(rotationAngles[0]);
        double accY = mAvgAccVal[1] * Math.cos(rotationAngles[1]);
        double accZ = mAvgAccVal[2] * Math.cos(rotationAngles[2]);
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
        //if (dist >= MIN_DISTANCE && dist <= MAX_DISTANCE) {
            mDistance.add(dist);
            mWriter3.writeData(dist);
            double[] geodetic = Projection.cartesianToGeodetic(accX, accY, accZ);
            mWriter2.writeData(geodetic[0], geodetic[1], geodetic[2]);
            updateMap(geodetic[0], geodetic[1]);
        //}
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

    void updateMap(final double lat, final double lon) {
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
        if (mSensorManager != null) {
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        } else  mActivity.runOnUiThread(() -> mActivity.mProgressDialog.dismiss());
    }

    public void stopSensors() {
        clearInitAcc();
        mSensorManager.unregisterListener(this);
    }

    public SensorBias getAccSensorBias() { return mSensorBias; }

    public boolean isAccCalibrationDone() { return mAccCalibrationDone; }

    public void clearInitAcc() { mAccInitialized = false; }

    public double getTotalDistance() {
        double dist = 0.;
        for(double distance: mDistance) {
            dist += distance;
        }
        return dist;
    }
}
