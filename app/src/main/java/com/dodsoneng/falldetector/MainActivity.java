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

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String[] INITIAL_PERMS={
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS
    };
    private static final String[] CAMERA_PERMS={
            Manifest.permission.CAMERA
    };
    private static final String[] CONTACTS_PERMS={
            Manifest.permission.READ_CONTACTS
    };
    private static final String[] LOCATION_PERMS={
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private static final int INITIAL_REQUEST=1337;
    private static final int CAMERA_REQUEST=INITIAL_REQUEST+1;
    private static final int CONTACTS_REQUEST=INITIAL_REQUEST+2;
    private static final int LOCATION_REQUEST=INITIAL_REQUEST+3;


    private void eula(Context context) {
        // Run the guardian
        Guardian.initiate(this);
        // Load the EULA
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.eula);
        dialog.setTitle("EULA");
        WebView web = (WebView) dialog.findViewById(R.id.eula);
        web.loadUrl("file:///android_asset/eula.html");
        Button accept = (Button) dialog.findViewById(R.id.accept);
        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Detector.initiate(this);
        setContentView(R.layout.actitvity_main);
        WebView web = (WebView) findViewById(R.id.about);
        web.loadUrl("file:///android_asset/about.html");
        Button help = (Button) findViewById(R.id.help);
        help.setOnClickListener(this);
        Button settings = (Button) findViewById(R.id.settings);
        settings.setOnClickListener(this);
        Button signals = (Button) findViewById(R.id.signals);

        if (!canAccessLocation() || !canAccessContacts()) {
            requestPermissions(INITIAL_PERMS, INITIAL_REQUEST);
        }

        signals.setOnClickListener(this);
        eula(this);
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        switch (view.getId()) {
            case R.id.settings:
                intent = new Intent(this, Settings.class);
                startActivity(intent);
                return;
            case R.id.help:
                Alarm.call(this);
                return;
            case R.id.signals:
                intent = new Intent(this, Signals.class);
                startActivity(intent);
                return;
        }
    }


    private boolean canAccessLocation() {
        return(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
    }

    private boolean canAccessCamera() {
        return(hasPermission(Manifest.permission.CAMERA));
    }

    private boolean canAccessContacts() {
        return(hasPermission(Manifest.permission.READ_CONTACTS));
    }

    private boolean hasPermission(String perm) {
        return(PackageManager.PERMISSION_GRANTED==checkSelfPermission(perm));
    }

}