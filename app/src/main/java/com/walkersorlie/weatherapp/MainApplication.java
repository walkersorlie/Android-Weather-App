package com.walkersorlie.weatherapp;

import android.app.Application;

import com.johnhiott.darkskyandroidlib.ForecastApi;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ForecastApi.create("API_KEY_HERE");
    }
}
