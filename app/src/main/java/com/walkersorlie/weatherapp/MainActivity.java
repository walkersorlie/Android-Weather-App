package com.walkersorlie.weatherapp;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int MY_PERMISSIONS_REQUEST_COARSE_LOCATION = 9000;
    private static final int MY_PERMISSIONS_REQUEST_ENABLE_LOCATION = 9001;
    private static final int MY_ERROR_DIALOG_REQUEST = 9002;
    private boolean locationPermissionsGranted = false;
    private TextView weatherTextView;
    private Button refreshWeatherButton;
    private WorkManager workManager;
    private FusedLocationProviderClient fusedLocationClient;
    private double latitude;
    private double longitude;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        weatherTextView = findViewById(R.id.weatherText);
        refreshWeatherButton = findViewById(R.id.refreshWeatherbutton);
        refreshWeatherButton.setOnClickListener(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        if (isPlayServicesOk()) {
            if (checkLocationSettings()) {
                if (locationPermissionsGranted) {
//                    startWorkers();
                    startWorker();
                } else {
                    getPermissions();
                }
            }
        }
    }


    @Override
    public void onClick(View view) {
        if (checkLocationSettings()) {
            getLastKnownLocation();
            Data locationData = new Data.Builder().putDouble("latitude", this.latitude).putDouble("longitude", this.longitude).build();
            OneTimeWorkRequest refreshWeather = new OneTimeWorkRequest.Builder(UpdateWeatherWorker.class).setInputData(locationData).build();
            workManager.enqueue(refreshWeather);

            workManager.getWorkInfoByIdLiveData(refreshWeather.getId())
                    .observe(this, new Observer<WorkInfo>() {
                        @Override
                        public void onChanged(@Nullable WorkInfo workInfo) {
                            if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                Data data = workInfo.getOutputData();
                                String weather = data.getString("weather");

                                weatherTextView.setText(weather);
                            }
                        }
                    });
        }
    }


    private void startWorkers() {
        getLastKnownLocation();

        // need to wait for getLastKnownLocation() to finish running first
        Data locationData = new Data.Builder().putDouble("latitude", this.latitude).putDouble("longitude", this.longitude).build();
        Log.d(TAG, "startWorkers, latitude: " + latitude);
        Log.d(TAG, "startWorkers, longitude: " + longitude);

        PeriodicWorkRequest updateWeatherWorker = new PeriodicWorkRequest.Builder(UpdateWeatherWorker.class, 1, TimeUnit.HOURS).setInputData(locationData).build();
        /**
         * Make this unique? What happens if I try to update notification info AND app info at the same time?
         */
        OneTimeWorkRequest updateWeatherWorkerInitial = new OneTimeWorkRequest.Builder(UpdateWeatherWorker.class).setInputData(locationData).build();
        workManager = WorkManager.getInstance(this);
        workManager.enqueue(updateWeatherWorkerInitial);
        workManager.enqueue(updateWeatherWorker);


        workManager.getWorkInfoByIdLiveData(updateWeatherWorkerInitial.getId())
                .observe(this, new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(@Nullable WorkInfo workInfo) {
                        if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            Data data = workInfo.getOutputData();
                            String weather = data.getString("weather");

                            Log.d(TAG, "onChanged, weather body: " + weather);
                            weatherTextView.setText(weather);
                        }
                    }
                });
    }


    private void startWorker() {
        getLastKnownLocation();

        // need to wait for getLastKnownLocation() to finish running first
        Data locationData = new Data.Builder().putDouble("latitude", this.latitude).putDouble("longitude", this.longitude).build();
        Log.d(TAG, "startWorkers, latitude: " + latitude);
        Log.d(TAG, "startWorkers, longitude: " + longitude);


        OneTimeWorkRequest updateWeatherWorker = new OneTimeWorkRequest.Builder(UpdateWeatherWorker.class).setInputData(locationData).build();
        workManager = WorkManager.getInstance(this);
        workManager.enqueue(updateWeatherWorker);


        workManager.getWorkInfoByIdLiveData(updateWeatherWorker.getId())
                .observe(this, new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(@Nullable WorkInfo workInfo) {
                        if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            Data data = workInfo.getOutputData();
                            String weather = data.getString("weather");

                            Log.d(TAG, "onChanged, weather body: " + weather);
                            weatherTextView.setText(weather);
                        }
                    }
                });
    }


    public void getLastKnownLocation() {
        fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();

                                Log.d(TAG, "getLastKnownLocation latitude: " + latitude);
                                Log.d(TAG, "getLastKnownLocation longitude: " + longitude);
                            }
                            else {
                                Log.d(TAG, "Location is null");
                            }
                        }
                    });
    }


    public boolean isPlayServicesOk(){
        Log.d(TAG, "isServicesOK: checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MainActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            //everything is fine and the user can make map requests
            Log.d(TAG, "isServicesOK: Google Play Services is working");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            //an error occured but we can resolve it
            Log.d(TAG, "isServicesOK: an error occured but we can fix it");
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(MainActivity.this, available, MY_ERROR_DIALOG_REQUEST);
            dialog.show();
        } else {
            Toast.makeText(this, "You can't make location requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }


    public boolean checkLocationSettings() {

        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            buildAlertMessageNoLocation();
            return false;
        }

        return true;
    }


    public void getPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("Required Location Permissions")
                        .setMessage("Location permission required for this app to work")
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_COARSE_LOCATION);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_COARSE_LOCATION);
            }
        }
        else {
            Log.d(TAG, "getPermissions, user has already given location permissions");
            locationPermissionsGranted = true;

            startWorker();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionsGranted = true;
                }
            }
        }
    }


    private void buildAlertMessageNoLocation() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("This application requires location services to work properly, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        Intent enableLocationIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(enableLocationIntent, MY_PERMISSIONS_REQUEST_ENABLE_LOCATION);
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: called.");

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ENABLE_LOCATION: {
                if(locationPermissionsGranted) {
                    Log.d(TAG, "onActivityResult: Have permissions");
                    startWorker();
                } else {
                    Log.d(TAG, "onActivityResult: Doesn't have permissions");
                    getPermissions();
                }
            }
        }
    }
}


