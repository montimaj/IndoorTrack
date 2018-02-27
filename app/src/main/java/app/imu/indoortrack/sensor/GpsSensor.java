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
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

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

    private static final String TAG = "GpsSensor";
    private static final String FILE_NAME = "GpsData.csv";
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 1;

    public GpsSensor(MapsActivity activity) {
        mActivity = activity;
        mFusedLocationClient = new FusedLocationProviderClient(mActivity);
        mSettingsClient = LocationServices.getSettingsClient(mActivity);
        mWriter = new SensorDataWriter(FILE_NAME, activity);
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
                    updateMap(location);
                }
            }
        };
    }

    private void updateMap(Location location) {
        GoogleMap map = mActivity.getMap();
        Log.d(TAG, location.toString());
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (map != null) {
            map.addMarker(new MarkerOptions().position(latLng).title("Marker in Current Place"));
            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }
        mWriter.writeData(latLng.latitude, latLng.longitude, location.getAltitude());

    }

    public void stopGps() {
        if(mFusedLocationClient != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }
}
