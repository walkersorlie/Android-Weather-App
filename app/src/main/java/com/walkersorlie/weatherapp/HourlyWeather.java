package com.walkersorlie.weatherapp;

import com.johnhiott.darkskyandroidlib.models.DataPoint;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class HourlyWeather {

    private long time;
    private TimeZone timezone;
    private double hourlyTemp;
    private String hourlyPrecipChance;
    private String hourlyPrecipType;


    public HourlyWeather(DataPoint hour, TimeZone timezone) {
        hourlyTemp = hour.getTemperature();
        hourlyPrecipChance = hour.getPrecipProbability();

        if (Double.valueOf(hour.getPrecipProbability()) != 0)
            hourlyPrecipType = hour.getPrecipType();
        else
            hourlyPrecipType = "None";

        time = hour.getTime();
        this.timezone = timezone;
    }


    public long getTime() {
        return time;
    }

    public String getFormattedTime() {
        Date date = new java.util.Date(time * 1000L);
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
        sdf.setTimeZone(timezone);
        return sdf.format(date);
    }

    public double getHourlyTemp() {
        return hourlyTemp;
    }

    public int getHourlyTempRounded() {
        return (int) Math.rint(hourlyTemp);
    }

    public String getHourlyPrecipChance() {
        return hourlyPrecipChance;
    }

    public int getFormattedHourlyPrecipChance() {
        return (int) Math.rint(Double.valueOf(hourlyPrecipChance) * 100);
    }

    public String getHourlyPrecipType() {
        return hourlyPrecipType;
    }

    @NotNull
    public String toString() {
        return getFormattedTime() + ", temp: " + getHourlyTempRounded() + "\u00B0F, precip: " + getFormattedHourlyPrecipChance() + "%, type: " + getHourlyPrecipType();
    }


    public void setTime(Long time) {
        this.time = time;
    }

    public void setHourlyTemp(double temp) {
        hourlyTemp = temp;
    }

    public void setHourlyPrecipChance(String chance) {
        hourlyPrecipChance = chance;
    }

    public void setHourlyPrecipType(String type) {
        hourlyPrecipType = type;
    }
}