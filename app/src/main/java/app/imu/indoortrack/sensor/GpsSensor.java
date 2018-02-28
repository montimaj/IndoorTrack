package app.imu.indoortrack.sensor;

import android.content.IntentSender;
import android.location.Location;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Vector;

import app.imu.indoortrack.MapsActivity;
import app.imu.indoortrack.io.SensorDataWriter;

public class GpsSensor {

    private MapsActivity mActivity;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationSettingsRequest mLocationSettingsRequest;
    private SensorDataWriter mWriter;
    private Vector<Double> mGpsVecX;
    private Vector<Double> mGpsVecY;
    private Vector<Double> mGpsVecZ;

    static SensorDataFilter sSensorDataFilterX;
    static SensorDataFilter sSensorDataFilterY;
    static SensorDataFilter sSensorDataFilterZ;

    static boolean sFilterInitialized = false;
    static double sGpsX;
    static double sGpsY;
    static double sGpsZ;

    private static double sXBias = 0.2d;
    private static double sYBias = 0.2d;
    private static double sZBias = 0.2d;

    private static final String TAG = "GpsSensor";
    private static final String FILE_NAME = "GpsData.csv";
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 500;
    private static final int GPS_CALIBRATION_ROUNDS = 100;

    private int mNumReadings;
    private boolean mCalibrate;

    public GpsSensor(MapsActivity activity) {
        mActivity = activity;
        mCalibrate = true;
        mFusedLocationClient = new FusedLocationProviderClient(mActivity);
        mSettingsClient = LocationServices.getSettingsClient(mActivity);
        mWriter = new SensorDataWriter(FILE_NAME, activity);
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
        mGpsVecX = new Vector<>();
        mGpsVecY = new Vector<>();
        mGpsVecZ = new Vector<>();
        mNumReadings = 0;

    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    public void startGps() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(mActivity, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        //noinspection MissingPermission
                        try {
                            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                    mLocationCallback, Looper.myLooper());
                        } catch (SecurityException e) { e.printStackTrace(); }

                    }
                })
                .addOnFailureListener(mActivity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(mActivity, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location: locationResult.getLocations()) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    double alt = location.getAltitude();
                    if (!mCalibrate) {
                        updateData(lat, lon, alt);
                    } else calibrateData(lat, lon, alt);
                }
            }
        };
    }

    private void updateData(double lat, double lon, double alt) {
        mWriter.writeData(lat, lon, alt);
        double[] cartesian = Projection.geodeticToCartesian(lat, lon, alt);
        sGpsX = cartesian[0];
        sGpsY = cartesian[1];
        sGpsZ = cartesian[2];
        if (!sFilterInitialized) {
            sSensorDataFilterX = new SensorDataFilter(sGpsX, sXBias, Accelerometer.sAccXBias);
            sSensorDataFilterY = new SensorDataFilter(sGpsY, sYBias, Accelerometer.sAccYBias);
            sSensorDataFilterZ = new SensorDataFilter(sGpsZ, sZBias, Accelerometer.sAccZBias);
            sFilterInitialized = true;
        }
    }

    private void calibrateData(double lat, double lon, double alt) {
        double[] cartesian = Projection.geodeticToCartesian(lat, lon, alt);
        double x = cartesian[0];
        double y = cartesian[1];
        double z = cartesian[2];
        if (mNumReadings <= GPS_CALIBRATION_ROUNDS) {
            mGpsVecX.add(x);
            mGpsVecY.add(y);
            mGpsVecZ.add(z);
            ++mNumReadings;
            System.out.println(mNumReadings + "," + x + "," + y + "," + z);
        } else {
            SensorBias sensorBias = new SensorBias(mGpsVecX, mGpsVecY, mGpsVecZ);
            sXBias = sensorBias.getBiasX();
            sYBias = sensorBias.getBiasY();
            sZBias = sensorBias.getBiasZ();
            mCalibrate = false;
        }
    }

    public void stopGps() {
        if(mFusedLocationClient != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }
}
