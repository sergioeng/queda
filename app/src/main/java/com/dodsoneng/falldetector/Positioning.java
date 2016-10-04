/*
The MIT License (MIT)

Copyright (c) 2016

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.dodsoneng.falldetector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.R.attr.action;

public class Positioning implements LocationListener {
    private static String TAG = "FD.POSNG";

    private static Positioning singleton = null;
    private Object lock = new Object();
    private final Context context;
    private Location gps;
    private Location network;
    private long once = 0;
    private boolean replied = true;
    private SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    public static void trigger() {

        Log.d (TAG, ".trigger(): in");


        if (null != singleton) {
            singleton.run();
        }
    }

    private Positioning(Context context) {
        Log.d (TAG, ".Positioning(): in");
        this.context = context;
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        reset();
    }

    public static Positioning initiate(Context context) {

        Log.d (TAG, "initiate():");

        if (null == singleton) {
            singleton = new Positioning(context);
        }

        return singleton;
    }

    public void terminate(Context ctx) {

        Log.d (TAG, "terminate():");
        LocationManager manager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        manager.removeUpdates(this);

        singleton = null;
    }

    private void run() {

        Log.d (TAG, ".run(): in");

        enforce(context);
        synchronized (lock) {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            once = System.currentTimeMillis();
            replied = false;
        }
    }

    private void reset() {

        Log.d (TAG, ".reset(): in");

        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        manager.removeUpdates(this);
        int meters10 = 10;
        int minutes10 = 10 * 60 * 1000;
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minutes10, meters10, this);
    }

    private static void enforce(Context context) {

        Log.d (TAG, ".enforce(): in");

        enforceWiFi(context);
        enforceGPS(context);
    }

    private static void enforceWiFi(Context context) {
        Log.d (TAG, ".enforceWiFi(): in");

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(true);

    }

    @SuppressWarnings("deprecation")
    private static void enforceGPS(Context context) {

        Log.d (TAG, ".enforceGPS(): in");

        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return;
        }
        boolean stealth = false;
        try {
            PackageManager packages = context.getPackageManager();
            PackageInfo info = packages.getPackageInfo("com.android.settings", PackageManager.GET_RECEIVERS);
            if (info != null) {
                for (ActivityInfo receiver : info.receivers) {
                    if (receiver.name.equals("com.android.settings.widget.SettingsAppWidgetProvider") && receiver.exported) {
                        stealth = true;
                    }
                }
            }
        } catch (NameNotFoundException ignored) {
        }
        if (stealth) {
            String provider = Secure.getString(context.getContentResolver(), Secure.LOCATION_PROVIDERS_ALLOWED);
            if (!provider.contains("gps")) {
                Intent poke = new Intent();
                poke.setClassName("com.android.settings", "com.android.settings.widget.SettingsAppWidgetProvider");
                poke.addCategory(Intent.CATEGORY_ALTERNATIVE);
                poke.setData(Uri.parse("3"));
                context.sendBroadcast(poke);
            }
        } else {
            Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private double accuracy(Location location) {
//        Log.d (TAG, ".accuracy(): in");

        if (null != location && location.hasAccuracy()) {
            return (location.getAccuracy());
        } else {
            return (Double.POSITIVE_INFINITY);
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        Log.d (TAG, ".onLocationChanged():");

        enforce(context);
        synchronized (lock) {
            if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {

                Log.d (TAG, ".onLocationChanged(): GPS accuracy: current("+gps.hasAccuracy()+ ") = "+gps.getAccuracy());
                Log.d (TAG, ".onLocationChanged(): GPS accuracy: lastknw("+location.hasAccuracy()+ ") = "+location.getAccuracy());

                if (accuracy(location) <= accuracy(gps)) {
                    Log.d (TAG, ".onLocationChanged(): GPS provider ... good accuracy ... using new GPS location");
                    gps = location;
                }
                else {
                    Log.d (TAG, ".onLocationChanged(): GPS provider ... bad  accuracy ... using old GPS location");
                }
            }
            if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
                Log.d (TAG, ".onLocationChanged(): NET accuracy: current("+network.hasAccuracy()+ ") = "+network.getAccuracy());
                Log.d (TAG, ".onLocationChanged(): NET accuracy: lastknw("+location.hasAccuracy()+ ") = "+location.getAccuracy());
                if (accuracy(location) <= accuracy(network)) {
                    Log.d (TAG, ".onLocationChanged(): NET provider ... good accuracy ... using new NET location");
                    /// BUG FIX by Sergio Eng: it was  =>  gps = location;
                    network = location;
                }
                else {
                    Log.d (TAG, ".onLocationChanged(): NET provider ... bad  accuracy ... using old NET location");
                }
            }

            long deadline = once + 120000;
            long now = System.currentTimeMillis();
            if (deadline <= now && !replied) {
                int battery = Battery.level(context);
                String message;
                if (Double.isInfinite(accuracy(gps)) && Double.isInfinite(accuracy(network))) {
                    message = "Battery: %d%%; Location unknown";
                    message = String.format(Locale.US, message, battery);
                } else {
                    if (accuracy(gps) <= accuracy(network)) {
                        Log.d (TAG, ".onLocationChanged(): Using GPS location");
                        location = gps;
                    } else {
                        Log.d (TAG, ".onLocationChanged(): Using NET location");
                        location = network;
                    }
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    int accuracy = (int) location.getAccuracy();
                    int altitude = (int) location.getAltitude();
                    int bearing = (int) location.getBearing();
                    int speed = (int) (location.getSpeed() * 60.0 * 60.0 / 1000.0);
                    String time = format.format(new Date(location.getTime()));
                    message = "Battery: %d%% Location: %s %.5f %.5f ~%dm ^%dm %ddeg %dkm/h http://maps.google.com/?q=%.5f,%.5f";
                    message = String.format(Locale.US, message, battery, time, lat, lon, accuracy, altitude, bearing, speed, lat, lon);
                }
                Messenger.sms(Contact.get(context), message);
                reset();
                replied = true;
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d (TAG, ".onStatusChanged(): provider="+provider+" status="+status);
        enforce(context);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d (TAG, ".onProviderEnabled(): provider="+provider);
        enforce(context);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d (TAG, ".onProviderDisabled(): provider="+provider);
        enforce(context);
    }
}
