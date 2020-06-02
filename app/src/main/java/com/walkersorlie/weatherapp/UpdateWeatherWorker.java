package com.walkersorlie.weatherapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.telecom.Call;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.futures.SettableFuture;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.common.util.concurrent.ListenableFuture;
import com.johnhiott.darkskyandroidlib.RequestBuilder;
import com.johnhiott.darkskyandroidlib.models.DataPoint;
import com.johnhiott.darkskyandroidlib.models.Request;
import com.johnhiott.darkskyandroidlib.models.WeatherResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;


public class UpdateWeatherWorker extends Worker {

    private NotificationCompat.Builder builder;
    private NotificationManager notificationManager;
    private final String CHANNEL_ID = "myweatherchannel";
    private String CHANNEL_NAME = "My weather channel";
    private static final String TAG = "Weather Body";
    private double latitude;
    private double longitude;


    public UpdateWeatherWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);

        builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        notificationManager = context.getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
            channel.setDescription("My weather notification channel");
            notificationManager.createNotificationChannel(channel);
        }

        latitude = getInputData().getDouble("latitude", 0);
        longitude = getInputData().getDouble("longitude", 0);
    }

    /**
     * @Todo error check for data. Don't assume there is data. Build response as I go
     * Make this call synchronous?? Already is??
     * Keep the thread blocking until getWeather is finished??
     * Looks like doWork() returns before getWeather finishes.
     * Do something with timeout. Retry probably?
     */
    @NonNull
    @Override
    public Result doWork() {
        Data returnWeather = new Data.Builder().putString("weather", getWeather()).build();

        return Result.success(returnWeather);
    }


    private String getWeather() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final String[] weatherInfo = {"Didn't work"};

        RequestBuilder weather = new RequestBuilder();

        Request request = new Request();
        request.setLat(Double.toString(latitude));
        request.setLng(Double.toString(longitude));
        request.setUnits(Request.Units.US);
        request.setLanguage(Request.Language.ENGLISH);
        request.addExcludeBlock(Request.Block.MINUTELY);
        request.addExcludeBlock(Request.Block.ALERTS);
        request.addExcludeBlock(Request.Block.FLAGS);


        weather.getWeather(request, new Callback<WeatherResponse>() {
            @Override
            public void success(WeatherResponse weatherResponse, Response response) {
                String notificationTitle = "Current weather";

                boolean currentWeatherFlag = true;
                String currentSummary = "No summary";
                int currentTemp = 0;
                int apparentTemp = 0;
                String currentlyBody = "No currently body";

                String notificationWeatherBodyDailyBlock;
                String appWeatherBody;


                /**
                 * Current weather summary and temperature
                 */
                if (weatherResponse.getCurrently() != null) {
                    currentSummary = weatherResponse.getCurrently().getSummary();
                    currentTemp = (int) Math.rint(weatherResponse.getCurrently().getTemperature());
                    apparentTemp = (int) Math.rint(weatherResponse.getCurrently().getApparentTemperature());
                    currentlyBody = currentSummary + ", " + currentTemp + "\u00B0F" + ", feels like " + apparentTemp + "\u00B0F";
                }
                else
                    currentWeatherFlag = false;


                /**
                 * 'Daily' data block temperature and precipitation information
                 */
                if (weatherResponse.getDaily().getData().size() > 0) {
                    DataPoint todayWeather = weatherResponse.getDaily().getData().get(0);
                    String dailySummary = todayWeather.getSummary();
                    int dailyMaxTemp = (int) Math.rint(todayWeather.getTemperatureMax());
                    int dailyMinTemp = (int) Math.rint(todayWeather.getTemperatureMin());
                    int dailyPrecipChance = (int) Math.rint(Double.valueOf(todayWeather.getPrecipProbability()) * 100);
                    String dailyPrecipType = "None";

                    if (Double.valueOf(todayWeather.getPrecipIntensity()) != 0)
                        dailyPrecipType = todayWeather.getPrecipType();

                    if (currentWeatherFlag) {
                        notificationWeatherBodyDailyBlock = currentlyBody + " \r\n" +
                                "Summary: " + dailySummary + " high: " + dailyMaxTemp + "\u00B0F, low: " + dailyMinTemp + "\u00B0F" +
                                ", precip: " + dailyPrecipChance + "%";

                        appWeatherBody = currentSummary + ", " + currentTemp + "\u00B0F" + ", feels like " + apparentTemp + "\u00B0F\r\n\r\n" +
                                "Summary: " + dailySummary + "\r\n" +
                                "High: " + dailyMaxTemp + "\u00B0F\r\n" +
                                "Low: " + dailyMinTemp + "\u00B0F\r\n" +
                                "Precipitation: " + dailyPrecipChance + "% chance";
                    }
                    else {
                        notificationWeatherBodyDailyBlock = "Summary: " + dailySummary + " high: " + dailyMaxTemp + "\u00B0F, low: " + dailyMinTemp + "\u00B0F" +
                                ", precip: " + dailyPrecipChance + "%, type: " + dailyPrecipType + "\r\n";

                        appWeatherBody = "Summary: " + dailySummary + "\r\n" +
                                "High: " + dailyMaxTemp + "\u00B0F\r\n" +
                                "Low: " + dailyMinTemp + "\u00B0F\r\n" +
                                "Precipitation: " + dailyPrecipChance + "% chance";
                    }

                    if (dailyPrecipChance != 0) {
                        notificationWeatherBodyDailyBlock = notificationWeatherBodyDailyBlock + ", type: " + dailyPrecipType;
                        appWeatherBody = appWeatherBody + ", type: " + dailyPrecipType;
                    }

                    appWeatherBody = appWeatherBody + "\r\n\r\nNext 4 hours:\r\n";
                }
                else {
                    notificationWeatherBodyDailyBlock = "No weather information for the day is available\r\n";
                    appWeatherBody = "No weather information for the day is available\r\n";
                }



                /**
                 * 'Hourly' data block temperature and precipitation information
                 */
                if (weatherResponse.getHourly().getData().size() >= 4) {
                    HourlyWeather[] hourlyWeather = new HourlyWeather[4];
                    List<DataPoint> hourlyWeatherDataPoints = weatherResponse.getHourly().getData();

                    if (Calendar.getInstance().get(Calendar.MINUTE) > 25) {
                        for (int i = 1; i < 5; i++) {
                            hourlyWeather[i - 1] = new HourlyWeather(hourlyWeatherDataPoints.get(i), TimeZone.getTimeZone(weatherResponse.getTimezone()));

                            notificationWeatherBodyDailyBlock = notificationWeatherBodyDailyBlock + hourlyWeather[i - 1].toString() + "\r\n";

                            appWeatherBody = appWeatherBody +
                                    hourlyWeather[i - 1].getFormattedTime() + ": temperature: " + hourlyWeather[i - 1].getHourlyTempRounded() + "\u00B0F, precipitation: " +
                                    hourlyWeather[i - 1].getFormattedHourlyPrecipChance() + "%";

                            if (hourlyWeather[i - 1].getFormattedHourlyPrecipChance() != 0)
                                appWeatherBody = appWeatherBody + ", type: " + hourlyWeather[i - 1].getHourlyPrecipType() + "\r\n";
                            else
                                appWeatherBody = appWeatherBody + "\r\n";
                        }
                    } else {
                        for (int i = 0; i < 4; i++) {
                            hourlyWeather[i] = new HourlyWeather(hourlyWeatherDataPoints.get(i), TimeZone.getTimeZone(weatherResponse.getTimezone()));

                            notificationWeatherBodyDailyBlock = notificationWeatherBodyDailyBlock + hourlyWeather[i].toString() + "\r\n";

                            appWeatherBody = appWeatherBody +
                                    hourlyWeather[i].getFormattedTime() + ": temperature: " + hourlyWeather[i].getHourlyTempRounded() + "\u00B0F, precipitation: " +
                                    hourlyWeather[i].getFormattedHourlyPrecipChance() + "%";

                            if (hourlyWeather[i].getFormattedHourlyPrecipChance() != 0)
                                appWeatherBody = appWeatherBody + ", type: " + hourlyWeather[i].getHourlyPrecipType() + "\r\n";
                            else
                                appWeatherBody = appWeatherBody + "\r\n";
                        }
                    }
                }
                else {
                    notificationWeatherBodyDailyBlock = notificationWeatherBodyDailyBlock + "No hourly weather information is available";
                    appWeatherBody = appWeatherBody + "No hourly weather information is available";
                }


                weatherInfo[0] = appWeatherBody;

                builder.setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(notificationTitle)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationWeatherBodyDailyBlock))
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                notificationManager.notify(1, builder.build());

                countDownLatch.countDown();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                builder.setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Error")
                        .setContentText(retrofitError.toString())
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                notificationManager.notify(1, builder.build());

                countDownLatch.countDown();
            }
            
         });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        Log.d(TAG, weatherInfo[0]);

        return weatherInfo[0];
    }


    private String getWeather2() {
        HttpURLConnection connection = null;

        try {
            String url = "https://api.darksky.net/forecast/";
            String charset = StandardCharsets.UTF_8.name();
            String apiKey = "5d6c6c11b5e855d01cd7331b60f33f3a";

            // use location services to get latitude and longitude here
            Double latitude = 344.058174;
            Double longitude = -121.315308;


            String params = String.format(
                    Locale.getDefault(),
                    "%1$s/%2$f,%3$f?exclude=currently,minutely,hourly,alerts,flags",
                    URLEncoder.encode(apiKey, charset),
                    latitude,
                    longitude
            );
            url = url + params;

            /**
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept-Charset", charset);
            connection.setRequestProperty("Accept-Encoding", "gzip");


            BufferedReader apiResponse;

            if ("gzip".equals(connection.getContentEncoding())) {
                apiResponse = new BufferedReader(new InputStreamReader(new GZIPInputStream(connection.getInputStream())));
            }
            else {
                apiResponse = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            }


            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = apiResponse.readLine()) != null) {
//                response.append(line);
//            }

            char[] chunk = new char[128];
            int value = apiResponse.read(chunk);
//            while ((value = apiResponse.read(chunk)) != -1) {
//                response.append(chunk, 0, value);
//                System.out.println(response.toString());
//            }

            response.append(chunk, 0, value);

            apiResponse.close();

            System.out.println(response.toString());
             **/

            String response = "Testing";

            String test = "{\"latitude\":37.1041,\"longitude\":3.3603,\"timezone\":\"Africa/Algiers\",\"daily\":{\"summary\":\"Rain on Saturday through next Thursday.\"}}";
            JSONObject json = new JSONObject(test);
            System.out.println(json);
            JSONObject obj = json.getJSONObject("daily");
            System.out.println(obj);
            JSONObject objLatitude = json.getJSONObject("latitude");
            System.out.println(objLatitude);


            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error bois";
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

}

