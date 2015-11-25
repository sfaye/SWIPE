/*
 *                         SWIPE Android Application
 *                Author: Sébastien FAYE [sebastien.faye@uni.lu]
 *
 * ------------------------------------------------------------------------------
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Sébastien FAYE [sebastien.faye@uni.lu], VehicularLab [vehicular-lab@uni.lu]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ------------------------------------------------------------------------------
 */

package com.sfaye.swipe;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.FloatMath;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import android.app.Service;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Calendar;

/*
    Get data from the sensors
 */
public class SensorService extends Service implements SensorEventListener{

    private volatile int HR_DELAY = 10; // Initial delay to get heart rate
    private volatile int HR_INTERVAL; // Heart rate recording interval (seconds) - adaptive
    private volatile int HR_SAMPLE = 0;
    private volatile float PRE_HR_DELAY = 10;
    private volatile GoogleApiClient mGoogleApiClient;
    private volatile SensorManager mSensorManager;
    private volatile Sensor mHeartRateSensor;
    private volatile Sensor mLinearAccelerationSensor;
    private volatile Sensor mStepCounterSensor;
    private volatile int nbHRupdate = 0;
    private volatile byte BatteryLevel = -1;
    public volatile int lastSync = 0;
    private volatile int lastBatteryUpdate = 0;
    private volatile int lastAccelTime = 0;
    private volatile int globalUpdate = 0;
    private volatile int globalTime = 0;
    private volatile int lastHRupdate = 0;
    private volatile int lastStepTime = 0;
    public volatile boolean doSynchro = false;
    public volatile boolean threadVerifData = false;
    private volatile boolean HRDelayTimeOK = false;
    private volatile String DATA;
    private volatile int lastHR = -1;
    private volatile float lastAccel = -1;
    private volatile float lastAccelTot = 0;
    private volatile float lastAccelAvg = 0;
    private volatile int stepCounter = -1;
    private volatile int stepCounterDiff = 0;
    private volatile int firstStepCounter = -1;
    private volatile int stepCounterPre = -1;
    private volatile int lastHRaccuracy = -1;
    public volatile int isHRactivated;
    private volatile int HRDelayTime = 0;
    private volatile boolean isRunning;
    private volatile boolean accessBlue = true;
    private volatile boolean isWaitingForSync;
    private volatile boolean itsTheEnd = false;
    private volatile boolean recordHR = false;
    public volatile BluetoothAdapter blue;
    private PowerManager.WakeLock wl;
    private volatile Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();

        DATA = new String("");
        isRunning = false;
        isWaitingForSync = false;
        settings = new Settings();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle connectionHint) {
                    trueSync();
                }
                @Override
                public void onConnectionSuspended(int cause) {}
            })
            .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult result) {}
            })
            .addApi(Wearable.API)
            .build();

        blue = BluetoothAdapter.getDefaultAdapter();

        mSensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mStepCounterSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mLinearAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "wlTag");
    }

    public void trueSync() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult nodes) {
            Node mNode = null;
            for (Node node : nodes.getNodes()) {
                mNode = node;
                break;
            }
            if (mNode != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mNode.getId(), "/wear-collect", DATA.getBytes()).setResultCallback(
                        new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess()) {
                                    DATA = new String("");
                                }
                            }
                        });
            }
            else { // No node detected: save data in a file
                try {
                    File file = new File(getFilesDir() + "/main_sgl");
                    if(file.exists()) {
                        file.delete();
                    }
                    file.createNewFile();
                    file.setReadable(true, false);

                    FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    bw.write(DATA);
                    bw.close();
                }
                catch (Exception e) {}
            }
            }
        });
    }

    public void Sync() {
        lastSync = globalTime;
        if(DATA.length() > 0) {
            if(!mGoogleApiClient.isConnected())
                mGoogleApiClient.connect();
            else
                trueSync();
        }
        isWaitingForSync = false;
        doSynchro = false;
        accessBlue = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            wl.acquire();
            lastSync = (int) (System.currentTimeMillis() / 1000);

            // If there is data from the previous session, we restore it
            try {
                File file = new File(getFilesDir() + "/main_sgl");
                if(file.exists()) {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    String data = "";
                    while ((line = br.readLine()) != null) {
                        data = data + line;
                    }
                    br.close();

                    DATA = DATA + data;
                    file.delete();

                    // Sync if there is enough data
                    if(data.length() >= 100) {
                        lastSync = (int) (System.currentTimeMillis() / 1000) - settings.getInt("TIMEOUT_SYNC") + 10;
                    }
                }
            }
            catch (Exception e) {}

            // ***
            mSensorManager.registerListener(this, this.mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mStepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mLinearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);

            Notification notification = new Notification(R.drawable.stat_notify_chat, "-", System.currentTimeMillis());
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            notification.setLatestEventInfo(this, "SWIPE", "Now recording", contentIntent);
            notification.flags|=Notification.FLAG_NO_CLEAR;
            startForeground(1, notification);

            HRDelayTime = (int) (System.currentTimeMillis() / 1000);
            HRDelayTimeOK = false;
            isHRactivated = 2;
            nbHRupdate = 0;
            isRunning = true;
            threadVerifData = true;

            if (blue.isEnabled())
                blue.disable();

            new Thread(new Runnable() {
                public void run() {
                    main();
                }
            }).start();
        }
        return Service.START_STICKY;
    }

    public void main() {
        while(threadVerifData) {
            globalTime = (int) (System.currentTimeMillis() / 1000);

            // Adaptive frequency
            if(globalTime - lastStepTime <= 10) { // Step detected recently
                HR_SAMPLE = 20;
                HR_INTERVAL = Math.max(settings.getInt("MIN_HR_INTERVAL") - HR_DELAY, 0); // Heart rate
                //settings.setInt("A_INTERVAL", 15); // Accelerometer
            }
            else { // Still
                HR_SAMPLE = 10;
                HR_INTERVAL = settings.getInt("MAX_HR_INTERVAL") - HR_DELAY; // Heart rate
                //settings.setInt("A_INTERVAL", 30); // Accelerometer
            }

            // HR sensor - activate
            if(isHRactivated == 0 && (globalTime - lastHRupdate >= HR_INTERVAL)) {
                HRDelayTime = globalTime;
                HRDelayTimeOK = false;
                lastHRupdate = 0;
                isHRactivated = 2;
                mSensorManager.registerListener(this, this.mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            // HR sensor - restart - bug
            if((isHRactivated == 2 && lastHRupdate > 0 && globalTime - lastHRupdate >= 30) || (lastHRupdate == 0 && HRDelayTime > 0 && globalTime - HRDelayTime >= 30)) {
                HRDelayTime = globalTime;
                HRDelayTimeOK = false;
                lastHRupdate = 0;
                mSensorManager.unregisterListener(this, this.mHeartRateSensor);
                mSensorManager.registerListener(this, this.mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            // Battery
            if(globalTime >= lastBatteryUpdate + settings.getInt("B_INTERVAL")) {
                lastBatteryUpdate = globalTime;
                registerReceiver(mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }

            // Add data
            if (globalTime > globalUpdate) {
                try {
                    // Acceleration
                    boolean recordAcceleration = false;
                    if(lastAccel > -1 && globalTime - lastAccelTime >= settings.getInt("A_INTERVAL"))  {
                        lastAccelTime = globalTime;
                        recordAcceleration = true;
                    }

                    // Steps
                    boolean recordStepCounter = false;
                    if(stepCounterDiff > 0 && globalTime - firstStepCounter >= settings.getInt("SC_INTERVAL")) {
                        firstStepCounter = -1;
                        recordStepCounter = true;
                    }

                    // Is there something to record?
                    if(recordAcceleration || recordHR || recordStepCounter || BatteryLevel > -1) {
                        globalUpdate = globalTime;

                        DATA = DATA + (BatteryLevel > -1 ? BatteryLevel:"") + "," + (recordHR ? lastHR:"") + "," + (recordHR ? lastHRaccuracy:"") + "," + (recordStepCounter ? stepCounterDiff:"") + "," + (recordAcceleration ? ((float) Math.round(lastAccel*10)/10.0):"") + "," + (recordAcceleration && lastAccelTot > 0 ? ((float) Math.round((lastAccelAvg/lastAccelTot)*10)/10.0):"") + ":" + globalUpdate + ";";

                        if(recordStepCounter) {
                            stepCounterPre = stepCounter;
                            stepCounterDiff = 0;
                        }
                        if(recordHR) {
                            lastHR = -1;
                            lastHRaccuracy = -1;
                            recordHR = false;
                        }
                        if(recordAcceleration) {
                            lastAccel = -1;
                            lastAccelTot = 0;
                            lastAccelAvg = 0;
                        }
                        BatteryLevel = -1;
                    }

                } catch (Exception e) {}
            }

            // Sync
            if (globalTime - lastSync >= settings.getInt("TIMEOUT_SYNC") && !doSynchro && (accessBlue || itsTheEnd)) {
                doSynchro = true;
                if(settings.getInt("TIMEOUT_SYNC") > 60 || (settings.getInt("TIMEOUT_SYNC") <= 60 && !itsTheEnd)) {
                    if(settings.getInt("TIMEOUT_SYNC") <= 60 && !itsTheEnd) {
                        itsTheEnd = true;
                    }
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                blue.enable();
                            }
                            catch(Exception e) {}
                        }
                    }).start();
                }
            }

            if (blue.isEnabled() && doSynchro && !isWaitingForSync) {
                isWaitingForSync = true;
                new Thread(new Runnable() {
                    public void run() {
                        if(!itsTheEnd) {
                            try {
                                Thread.sleep(50000);
                            } catch (Exception e) {}
                        }
                        Sync();
                    }
                }).start();
            }

            if (globalTime - lastSync >= settings.getInt("TIMEOUT_SYNC") + 30 && doSynchro && !isWaitingForSync) {
                lastSync = lastSync + 30;
                doSynchro = false;
                itsTheEnd = false;
            }

            if (globalTime - lastSync >= 10 && !accessBlue && !itsTheEnd) {
                new Thread(new Runnable() {
                    public void run() {
                        mGoogleApiClient.disconnect();
                        blue.disable();
                    }
                }).start();
                accessBlue = true;
            }

            try {
                Thread.sleep(1000);
            }
            catch(Exception e) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;
        globalTime = (int) (System.currentTimeMillis() / 1000);

        if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) { // Accelerometer without gravity
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            float speed = FloatMath.sqrt(x*x+y*y+z*z); // Acceleration
            lastAccelAvg += speed;
            lastAccelTot += 1;

            if(speed > lastAccel)
                lastAccel = speed;
        }
        else if (mySensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            stepCounter = (int) sensorEvent.values[0];
            if(firstStepCounter == -1)
                firstStepCounter = globalTime;
            if(stepCounterPre == -1)
                stepCounterPre = stepCounter;
            stepCounterDiff = stepCounter - stepCounterPre;
            if(stepCounterDiff > 0)
                lastStepTime = globalTime;
        }
        else { // Heart rate sensor
            if (sensorEvent.values[0] > 0) {
                // Delay to get data
                if(HRDelayTime > 0 && !HRDelayTimeOK) {
                    HRDelayTimeOK = true;
                    HR_DELAY = (int) ((globalTime - HRDelayTime) * 0.25 + PRE_HR_DELAY * 0.75);
                    PRE_HR_DELAY = new Float(HR_DELAY);
                    HRDelayTime = 0;
                }

                if(sensorEvent.accuracy >= lastHRaccuracy) {
                    lastHR = (int) sensorEvent.values[0];
                    lastHRaccuracy = sensorEvent.accuracy;
                }

                lastHRupdate = globalTime;
                nbHRupdate++;

                // Deactivate
                if(nbHRupdate >= 1 + HR_SAMPLE || lastHRaccuracy >= 2) {
                    nbHRupdate = 0;
                    isHRactivated = 0;
                    recordHR = true;
                    mSensorManager.unregisterListener(this, this.mHeartRateSensor);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    @Override
    public boolean stopService(Intent name) {
        return super.stopService(name);
    }

    @Override
    public void onDestroy() {
        if (!blue.isEnabled())
            blue.enable();

        if(isRunning) {
            Sync();
            wl.release();
            threadVerifData = false;
            isRunning = false;
            isHRactivated = 0;
            mSensorManager.unregisterListener(this);
            try {
                unregisterReceiver(mBatInfoReceiver);
            }
            catch (Exception e) {}
            stopForeground(true);
        }
    }

    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            BatteryLevel = (byte) (intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            if(BatteryLevel <= 0)
                BatteryLevel = 1;
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if((BatteryLevel > 0 && BatteryLevel < settings.getInt("MIN_BATTERY")) || (settings.getInt("NIGHT_STOP") == 1 && (hour >= 0 && hour <= 5))) {
                settings.setInt("TIMEOUT_SYNC", 60);
            }
            unregisterReceiver(mBatInfoReceiver);
        }
    };
}
