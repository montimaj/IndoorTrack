package app.imu.indoortrack;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import app.imu.indoortrack.io.FileService;
import app.imu.indoortrack.sensor.InertialSensor;
import app.imu.indoortrack.sensor.GpsSensor;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private InertialSensor mInertialSensor;
    private GpsSensor mGps;
    private SharedPreferences mSharedPrefs;

    private boolean mSensorsStarted;

    public ProgressDialog mProgressDialog;

    private static final int REQUEST_ACCESS_CODE = 1001;

    public static final String SHARED_PREFS_NAME = "IndoorTrack";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mSharedPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        Button startButton = findViewById(R.id.start);
        Button stopButton = findViewById(R.id.stop);
        startButton.setOnClickListener(view -> {
            if (!mSensorsStarted) {
                Toast.makeText(this, "Started Indoor Tracking", Toast.LENGTH_LONG).show();
                startService(new Intent(this, FileService.class));
                getPermissions();
                mSensorsStarted = true;
            }
        });
        stopButton.setOnClickListener(view -> {
            if (mInertialSensor != null) {
                Toast.makeText(this, "Total Distance = " +
                        mInertialSensor.getTotalDistance() + "m", Toast.LENGTH_LONG).show();
                stopSensors();
            }
        });
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) { mMap = googleMap; }

    @Override
    public void onPause() { super.onPause(); }

    @Override
    public void onResume() { super.onResume(); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSensors();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ACCESS_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSensors();
                } else {
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void startSensors() {
        new Thread(() -> {
            if (mInertialSensor == null) {
                mInertialSensor = new InertialSensor(MapsActivity.this);
            }
            if (mGps == null) {
                mGps = new GpsSensor(MapsActivity.this, mInertialSensor);
            }
            mInertialSensor.startSensors();
            mGps.startGps();
        }).start();
    }

    private void stopSensors() {
        stopService(new Intent(this, FileService.class));
        mSensorsStarted = false;
        if(mInertialSensor != null) {
            mInertialSensor.stopSensors();
            mInertialSensor = null;
        }
        if (mGps != null) {
            mGps.stopGps();
            mGps = null;
        }
    }

    public GoogleMap getMap() { return mMap; }

    private void getPermissions() {
        boolean hasPermission1 = (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        boolean hasPermission2 = (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission1 || !hasPermission2) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_CODE);
        } else startSensors();
    }

    public SharedPreferences getSharedPrefs() { return mSharedPrefs; }
}
