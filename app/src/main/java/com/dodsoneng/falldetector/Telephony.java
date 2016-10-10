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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import java.util.Date;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Method;

public class Telephony extends BroadcastReceiver {

    private static String TAG = "FD.TELPH";

    private static int undo = -1;
/*** TO BE DELETED
    static PhonecallStartEndDetector listener=null;
***/

    public static void handsfree(Context context) {
        Log.d(TAG, "handsfree(): undo="+undo);
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.setMode(AudioManager.MODE_IN_CALL);
        manager.setSpeakerphoneOn(true);
        Log.d(TAG, "handsfree(): SpeakerPhone=TRUE");
        Alarm.loudest(context, AudioManager.STREAM_VOICE_CALL);
        Log.d(TAG, "handsfree(): out");
    }

    public static void silence(Context context) {
        Log.d(TAG, "silence(): undo="+undo);
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            Method getITelephony = manager.getClass().getDeclaredMethod("getITelephony");
            getITelephony.setAccessible(true);
            Object iTelephony = getITelephony.invoke(manager);
            Method silenceRinger = iTelephony.getClass().getDeclaredMethod("silenceRinger");
            silenceRinger.invoke(iTelephony);
            undo = -1;
        } catch (Throwable ignored) {
            AudioManager manager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            undo = manager.getRingerMode();
            manager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
    }

    public static void ringing(Context context) {
        Log.d(TAG, "ringing(): undo="+undo);
        AudioManager manager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (-1 != undo) {
            manager.setRingerMode(undo);
        }
        Log.d(TAG, "ringing(): SpeakerPhone=FALSE");
        manager.setSpeakerphoneOn(false);
    }


    private static void answer(Context context) {
        Log.d(TAG, "answer(): undo="+undo);
        silence(context);
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            Method getITelephony = manager.getClass().getDeclaredMethod("getITelephony");
            getITelephony.setAccessible(true);
            Object iTelephony = getITelephony.invoke(manager);
            Method answerRingingCall = iTelephony.getClass().getDeclaredMethod("answerRingingCall");
            answerRingingCall.invoke(iTelephony);
        }
        catch (Throwable throwable) {
            Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON);
            down.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
            context.sendOrderedBroadcast(down, "android.permission.CALL_PRIVILEGED");
            Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON);
            up.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
            context.sendOrderedBroadcast(up, "android.permission.CALL_PRIVILEGED");
        }
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
/*** TO BE DELETED
        if (listener == null)
            listener = new PhonecallStartEndDetector ();

        manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
***/
        switch (manager.getCallState()) {
            case TelephonyManager.CALL_STATE_RINGING:
                Log.d(TAG, "onReceive(): CALL_STATE_RINGING");
                String contact = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                if (Contact.check(context, contact)) {
                    answer(context);
                }
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                Log.d(TAG, "onReceive(): CALL_STATE_OFFHOOK");
                handsfree(context);
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                Log.d(TAG, "onReceive(): CALL_STATE_IDLE");
                ringing(context);
                break;
            default:
                Log.d(TAG, "onReceive(): CALL_STATE_UNKNOWN");
                break;
        }
    }


/*** TO BE DELETED

 //--------------------------------------------------------------------------------
    //Derived classes should override these to respond to specific events of interest
    protected void onIncomingCallStarted(String number, Date start) {}
    protected void onOutgoingCallStarted(String number, Date start) {}
    protected void onIncomingCallEnded(String number, Date start, Date end) {}
    protected void onOutgoingCallEnded(String number, Date start, Date end) {}
    protected void onMissedCall(String number, Date start) {}

    //Deals with actual events
    public class PhonecallStartEndDetector extends PhoneStateListener {
        private String TAG = "FD.TELPH.PSLIS";

        int lastState = TelephonyManager.CALL_STATE_IDLE;
        Date callStartTime;
        boolean isIncoming;
        String savedNumber;  //because the passed incoming is only valid in ringing

        public PhonecallStartEndDetector() {
        }

        //The outgoing number is only sent via a separate intent, so we need to store it out of band
        public void setOutgoingNumber(String number) {
            savedNumber = number;
        }

        //Incoming call-  goes from IDLE to RINGING when it rings, to OFFHOOK when it's answered, to IDLE when its hung up
        //Outgoing call-  goes from IDLE to OFFHOOK when it dials out, to IDLE when hung up
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            Log.d (TAG, "onCallStateChanged(): state="+state+" lastState="+lastState);

            super.onCallStateChanged(state, incomingNumber);
            if (lastState == state) {
                //No change, debounce extras
                return;
            }
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    Log.d (TAG, "onCallStateChanged(): state=CALL_STATE_RINGING");
                    isIncoming = true;
                    callStartTime = new Date();
                    savedNumber = incomingNumber;
                    onIncomingCallStarted(incomingNumber, callStartTime);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log.d (TAG, "onCallStateChanged(): state=CALL_STATE_OFFHOOK");
                    //Transition of ringing->offhook are pickups of incoming calls.  Nothing donw on them
                    if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                        isIncoming = false;
                        callStartTime = new Date();
                        onOutgoingCallStarted(savedNumber, callStartTime);
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    Log.d (TAG, "onCallStateChanged(): state=CALL_STATE_IDLE");
                    //Went to idle-  this is the end of a call.  What type depends on previous state(s)
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        //Ring but no pickup-  a miss
                        onMissedCall(savedNumber, callStartTime);
                    } else if (isIncoming) {
                        onIncomingCallEnded(savedNumber, callStartTime, new Date());
                    } else {
                        onOutgoingCallEnded(savedNumber, callStartTime, new Date());
                    }
                    break;
            }
            lastState = state;
        }
    }
        ***/
}
