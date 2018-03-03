package app.imu.indoortrack.sensor;

import android.content.IntentSender;
import android.location.Location;
import android.os.Looper;
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
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;

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
    private Location mCurrLocation;
    private InertialSensor mInertialSensor;

    static SensorDataFilter sSensorDataFilterX;
    static SensorDataFilter sSensorDataFilterY;
    static SensorDataFilter sSensorDataFilterZ;

    static boolean sFilterInitialized = false;
    static double sGpsX;
    static double sGpsY;
    static double sGpsZ;

    private static final String TAG = "GpsSensor";
    private static final String FILE_NAME = "GpsData.csv";
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 500;
    private static final double GPS_CHANGE_THRESHOLD = 0.01d;

    public GpsSensor(MapsActivity activity, InertialSensor acc) {
        mActivity = activity;
        mFusedLocationClient = new FusedLocationProviderClient(mActivity);
        mSettingsClient = LocationServices.getSettingsClient(mActivity);
        mWriter = new SensorDataWriter(FILE_NAME, activity);
        mInertialSensor = acc;
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
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
                .addOnSuccessListener(mActivity, locationSettingsResponse -> {
                    Log.i(TAG, "All location settings are satisfied.");

                    //noinspection MissingPermission
                    try {
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());
                    } catch (SecurityException e) { e.printStackTrace(); }

                })
                .addOnFailureListener(mActivity, e -> {
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
                });
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (mInertialSensor.isAccCalibrationDone()) {
                    Location location = locationResult.getLastLocation();
                    boolean hasAcceptableChange = false;
                    if (!sFilterInitialized || (hasAcceptableChange = acceptableChange(mCurrLocation, location))) {
                        mCurrLocation = location;
                        if (hasAcceptableChange)    mInertialSensor.clearInitAcc();
                        updateData(mCurrLocation);
                    }
                }
            }
        };
    }

    private boolean acceptableChange(Location location1, Location location2) {
        return Math.abs(location1.getLatitude() - location2.getLatitude()) > GPS_CHANGE_THRESHOLD ||
                Math.abs(location1.getLongitude() - location2.getLongitude()) > GPS_CHANGE_THRESHOLD ||
                Math.abs(location1.getAltitude() - location2.getAltitude()) > GPS_CHANGE_THRESHOLD;
    }

    private void updateData(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        double alt = location.getAltitude();
        mWriter.writeData(lat, lon, alt);
        double[] cartesian = Projection.geodeticToCartesian(lat, lon, alt);
        sGpsX = cartesian[0];
        sGpsY = cartesian[1];
        sGpsZ = cartesian[2];
        if (!sFilterInitialized) {
            mInertialSensor.updateMap(lat, lon);
            double horizontalAccuracy = location.getAccuracy()/100d;
            SensorBias sensorBias = mInertialSensor.getAccSensorBias();
            double accXBias  = sensorBias.getBiasX();
            double accYBias = sensorBias.getBiasY();
            double accZBias = sensorBias.getBiasZ();
            System.out.println("Bias = " + accXBias + "," + accYBias + "," + accZBias);
            sSensorDataFilterX = new SensorDataFilter(sGpsX, horizontalAccuracy, accXBias);
            sSensorDataFilterY = new SensorDataFilter(sGpsY, horizontalAccuracy, accYBias);
            sSensorDataFilterZ = new SensorDataFilter(sGpsZ, 0, accZBias);
            sFilterInitialized = true;
        }
    }

    public void stopGps() {
        sFilterInitialized = false;
        if(mFusedLocationClient != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }
}
