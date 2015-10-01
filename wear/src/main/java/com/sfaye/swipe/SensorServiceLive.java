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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.FloatMath;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.util.Log;

/*
    Get data from the sensors with the live mode (a few minutes data collection). This file is similar to the SensorService file, but is dedicated to the retrieval of data in live
        (frequencies of about 1 second, voice recognition, gyro, etc.)
 */

public class SensorServiceLive extends Service implements SensorEventListener{

    private volatile Sensor mHeartRateSensor;
    private volatile int BatteryLevel = -1;
    private volatile Sensor mLinearAccelerationSensor;
    private volatile Sensor mRotationVectorSensor;
    private volatile Sensor mStepCounterSensor;
    private volatile SensorManager mSensorManager;
    public volatile long lastSync = 0;
    private volatile long lastBatteryUpdate = 0;
    public volatile boolean doSynchro = false;
    public volatile boolean threadVerifData = false;
    private volatile GoogleApiClient mGoogleApiClient;
    private volatile ArrayList<String> listBDD;
    private volatile float lastHR = -1;
    private volatile float lastAccel = -1;
    private volatile float stepCounter = -1;
    private volatile String lastGestureRecognition = ",";
    private volatile String fullLinearAccelVector = "##";
    private volatile String fullRotationVector = "##";
    private volatile float stepCounterDiff = 0;
    private volatile float stepCounterPre = -1;
    private volatile long globalUpdate = 0;
    private volatile float lastYPattern = -1;
    private volatile long globalTime = 0;
    private volatile long lastHRupdate = 0;
    private volatile int lastHRaccuracy = -1;
    public volatile int isHRactivated;
    private volatile long HRDelayTime = 0;
    private volatile boolean mRunning;
    private PowerManager.WakeLock wl;
    private volatile Node mNode;

    @Override
    public void onCreate() {
        super.onCreate();

        mRunning = false;

        listBDD = new ArrayList<String>();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        resolveNode();
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

        mSensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mStepCounterSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mLinearAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        // Get the Wake Lock
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "wlTag");
    }

    public void Sync() {
        lastSync = globalTime / 1000;
        ArrayList<String> BDDtoSync = new ArrayList<String>(listBDD);
        listBDD.clear();

        String data = ReadSettings(BDDtoSync);
        PutDataMapRequest dataMap = PutDataMapRequest.create("/synclive");
        dataMap.getDataMap().putString("data", data);
        PutDataRequest request = dataMap.asPutDataRequest();
        try {
            /*Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataItemResult>() {
                        @Override
                        public void onResult(DataItemResult dataItemResult) {
                            doSynchro = false;
                        }
                    });*/

            Wearable.MessageApi.sendMessage(mGoogleApiClient, mNode.getId(), "/wear-collect", data.getBytes()).setResultCallback(
                    new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            if (sendMessageResult.getStatus().isSuccess()) {
                                doSynchro = false;
                            }
                        }
                    });
        }
        catch (Exception e) {
            doSynchro = false;
            listBDD.addAll(BDDtoSync);
        }
    }

    private void resolveNode() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                for (Node node : nodes.getNodes()) {
                    mNode = node;
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mRunning) {
            wl.acquire();

            mRunning = true;
            mGoogleApiClient.connect();
            mSensorManager.registerListener(this, this.mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mStepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mLinearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mRotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
            isHRactivated = 2;

            Notification notification = new Notification(R.drawable.stat_notify_chat, "Sensors?", System.currentTimeMillis());
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            notification.setLatestEventInfo(this, "SWIPE Live", "Now recording", contentIntent);
            notification.flags|=Notification.FLAG_NO_CLEAR;
            startForeground(1, notification);

            threadVerifData = true;
            new Thread(new Runnable() {
                public void run() {
                    verifData();
                }
            }).start();
        }
        return Service.START_STICKY;
    }

    public void verifData() {
        while(threadVerifData) {
            globalTime = System.currentTimeMillis();
            long globalTimeS = globalTime / 1000;

            // Heart rate sensor - but - restart
            if((isHRactivated == 2 && lastHRupdate > 0 && globalTimeS - lastHRupdate >= 30) || (lastHRupdate == 0 && HRDelayTime > 0 && globalTimeS - HRDelayTime >= 30)) {
                HRDelayTime = globalTimeS;
                lastHRupdate = 0;
                mSensorManager.unregisterListener(this, this.mHeartRateSensor);
                mSensorManager.registerListener(this, this.mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            // Battery
            if(globalTimeS >= lastBatteryUpdate + 20) {
                lastBatteryUpdate = globalTimeS;
                registerReceiver(mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }

            lastYPattern = -1;

            // Add
            try {
                if(lastHR > -1 || stepCounterDiff > 0 || BatteryLevel > -1 || lastGestureRecognition != ",") {
                    globalUpdate = globalTimeS;

                    listBDD.add((BatteryLevel > -1 ? BatteryLevel:"") + "," + (lastHR > -1 ? lastHR:"") + "," + (lastHRaccuracy > -1 ? lastHRaccuracy:"") + "," + (stepCounterDiff > 0 ? stepCounterDiff:"") + "," + (lastAccel > -1 ? lastAccel:"") + "," + fullLinearAccelVector + "," + fullRotationVector + "," + (lastAccel <= 5 ? lastGestureRecognition : ",") + ":" + globalUpdate);

                    if(lastAccel <= 5) {
                        lastGestureRecognition = ",";
                    }
                    stepCounterPre = stepCounter;
                    stepCounterDiff = 0;
                    lastHR = -1;
                    lastHRaccuracy = -1;
                    lastAccel = -1;
                    BatteryLevel = -1;
                    fullLinearAccelVector = "##";
                    fullRotationVector = "##";
                }

            } catch (Exception e) {}

            // Sync
            if (!doSynchro) {
                doSynchro = true;
                new Thread(new Runnable() {
                    public void run() {
                        Sync();
                    }
                }).start();
            }
            if (globalTimeS - lastSync >= 5 && doSynchro) {
                doSynchro = false;
            }

            try {
                Thread.sleep(500);
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
        globalTime = System.currentTimeMillis();
        long globalTimeS = globalTime / 1000;

        if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) { // Accelerometer without gravity
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            float speed = FloatMath.sqrt(x*x+y*y+z*z);

            if(speed > lastAccel) {
                lastAccel = speed;
                fullLinearAccelVector = String.valueOf(x) + "#" + String.valueOf(y) + "#" + String.valueOf(z);
            }

            if(y > lastYPattern) {
                lastYPattern = y;
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_ROTATION_VECTOR) { // Rotation vector for orientation
            float[] rotationMatrix=new float[16];
            mSensorManager.getRotationMatrixFromVector(rotationMatrix,sensorEvent.values);
            float[] orientationValues = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientationValues);
            float azimuth = (float)(orientationValues[0]);
            float pitch = (float)(orientationValues[1]);
            float roll = (float)(orientationValues[2]);

            //if(azimuth < 0.0f) azimuth += 360.0f;
            //if(pitch < 0.0f) pitch += 360.0f;
            //if(roll < 0.0f) roll += 360.0f;

            /* Example of pattern

            float pitchB = Math.round(Math.toDegrees(pitch));
            float rollB = Math.round(Math.toDegrees(roll));
        
            if(pitchB > 50 && ((rollB > 20 && rollB < 150) || (rollB > -170 && rollB < -40)) && (lastYPattern > 4 || lastYPattern < -4)) {
                Log.d("test", "pattern 1");
            }*/

            fullRotationVector = String.valueOf(azimuth) + "#" + String.valueOf(pitch) + "#" + String.valueOf(roll);
        }
        else if (mySensor.getType() == Sensor.TYPE_STEP_COUNTER) { // Step counter
            stepCounter = sensorEvent.values[0];
            if(stepCounterPre == -1)
                stepCounterPre = stepCounter;
            stepCounterDiff = stepCounter - stepCounterPre;
        }
        else { // Heart rate
            if (sensorEvent.values[0] > 0) {
                lastHRupdate = globalTimeS;
                lastHR = sensorEvent.values[0];
                lastHRaccuracy = sensorEvent.accuracy;
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
        if(mRunning) {
            wl.release();
            threadVerifData = false;
            mRunning = false;
            isHRactivated = 0;
            Sync();
            mSensorManager.unregisterListener(this);
            try {
                unregisterReceiver(mBatInfoReceiver);
            }
            catch (Exception e) {}
            mGoogleApiClient.disconnect();
            stopForeground(true);
        }
    }

    public String ReadSettings(List<String> listBDDtoRead){
        String BDDdata = "";

        for (String s : listBDDtoRead) {
            BDDdata = BDDdata + s + ";";
        }

        return BDDdata;
    }

    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            BatteryLevel = (int) (intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            unregisterReceiver(mBatInfoReceiver);
        }
    };
}