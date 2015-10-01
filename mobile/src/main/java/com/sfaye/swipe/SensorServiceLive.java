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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.FloatMath;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import java.util.ArrayList;
import java.util.List;

/*
    Get data from the sensors with the live mode (a few minutes data collection). This file is similar to the SensorService file, but is dedicated to the retrieval of data in live
        (frequencies of about 1 second, voice recognition, gyro, etc.)
 */
public class SensorServiceLive extends Service implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    /*
        Parameters
     */
    public final String liveServiceUrl = "http://server/service_live_mobile_post.php"; // Live web service - mobile


    /*
        ############################################
    */
    public LocationManager locationManager;
    Context thisContext = null;
    private volatile String lastPositions = ";;;";
    private volatile String lastSPSensorData = ";;;";
    private volatile String lastMobileInfos = ";;;;;";
    private volatile String lastWifiInfos = ";";
    private volatile String lastBluetoothInfos = ";";
    private volatile String lastMotions = ";";
    private volatile String BatteryLevel = "";
    private volatile float lastAudios = -1;
    private volatile String isHuman = "";
    private volatile String humanText = "";
    private volatile float lastAudiosEWMA = 0;
    private GoogleApiClient mGoogleApiClient;
    public LocationListener locationListener;
    private SensorManager mSensorManager;
    private volatile long lastSensorUpdate = 0;
    private volatile long lastWifiUpdate = 0;
    private volatile long lastBatteryUpdate = 0;
    private volatile long lastBluetoothUpdate = 0;
    private volatile long lastMobileInfosUpdate = 0;
    private Sensor mProximitySensor;
    private Sensor mLightSensor;
    private Sensor mLinearAccelerationSensor;
    private volatile Sensor mStepCounterSensor;
    private volatile float lastProximity = -1;
    private volatile float lastLight = -1;
    private volatile float lastAccel = -1;
    private volatile boolean isBluetoothScan = false;
    private volatile float maxProximity = -1;
    private volatile int totNbBluetooth = 0;
    private volatile String totBluetooth = "";
    private volatile float maxLight = -1;
    private ActivityRecognitionScan mActivityRecognitionScan = new ActivityRecognitionScan(this);
    private BroadcastReceiver updateReceiver;
    private PowerManager.WakeLock wl;
    private WifiManager mainWifi;
    private WifiReceiver receiverWifi;
    private volatile float stepCounter = -1;
    private volatile float stepCounterDiff = 0;
    private volatile float stepCounterPre = -1;
    private volatile boolean microRunning = true;
    private volatile boolean mRunning;
    BluetoothAdapter bluetoothAdapter;

    TelephonyManager teleMan;
    Thread mainSave;

    @Override
    public void onCreate() {

        super.onCreate();

        teleMan = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        receiverWifi = new WifiReceiver();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        thisContext = getBaseContext();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if(location != null)
                    updateLoc(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        // Proximity sensor
        mSensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        mLinearAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mStepCounterSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        m_ctx = this;
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Get the Wake Lock
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "wlTag");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mRunning) {
            mRunning = true;
            wl.acquire();

            mSensorManager.registerListener(this, this.mLinearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mStepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1 * 1000, 0, locationListener);

            // Activity manager
            mActivityRecognitionScan.startActivityRecognitionScan();
            updateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    lastMotions = intent.getExtras().getString("activity");
                }
            };
            IntentFilter updateIntentFilter = new IntentFilter("updatedActivity");
            registerReceiver(updateReceiver, updateIntentFilter);

            // Micro
            startListening();

            // Main thread
            Runnable mainSaveCall = new Runnable() {
                public void run() {
                    while(mRunning) {
                        long currentTime = System.currentTimeMillis() / 1000;
                        if(currentTime >= lastMobileInfosUpdate + 60) {
                            lastMobileInfosUpdate = currentTime;
                            try {
                                String asuLevel = "";
                                if(teleMan.getAllCellInfo() != null) {
                                    for (final CellInfo info : teleMan.getAllCellInfo()) {
                                        if (info instanceof CellInfoGsm)
                                            asuLevel = String.valueOf((((CellInfoGsm) info).getCellSignalStrength()).getAsuLevel());
                                        else if (info instanceof CellInfoCdma)
                                            asuLevel = String.valueOf((((CellInfoCdma) info).getCellSignalStrength()).getAsuLevel());
                                        else if (info instanceof CellInfoWcdma)
                                            asuLevel = String.valueOf((((CellInfoWcdma) info).getCellSignalStrength()).getAsuLevel());
                                        else if (info instanceof CellInfoLte)
                                            asuLevel = String.valueOf((((CellInfoLte) info).getCellSignalStrength()).getAsuLevel());
                                        break;
                                    }
                                }

                                lastMobileInfos = String.valueOf(teleMan.getNetworkType()) + ";" + String.valueOf(teleMan.getDataState()) + ";" + asuLevel + ";" + String.valueOf(teleMan.getNetworkOperator()) + ";" + String.valueOf(teleMan.getNetworkOperatorName()) + ";" + String.valueOf(teleMan.getNetworkCountryIso());
                            }
                            catch (Exception e) {}
                        }

                        if(currentTime >= lastWifiUpdate + 20) {
                            lastWifiUpdate = currentTime;
                            try {
                                registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                                mainWifi.startScan();
                            }
                            catch (Exception e) {}
                        }

                        if(currentTime >= lastBluetoothUpdate + 20) {
                            try {
                                registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                                bluetoothAdapter.startDiscovery();
                                isBluetoothScan = true;
                                lastBluetoothUpdate = currentTime;
                            }
                            catch (Exception e) {}

                        }

                        if(currentTime >= lastBluetoothUpdate + 10 && isBluetoothScan) {
                            lastBluetoothUpdate = currentTime;
                            isBluetoothScan = false;
                            unregisterReceiver(BluetoothReceiver);
                            lastBluetoothInfos = totBluetooth + ";" + String.valueOf(totNbBluetooth);
                            totBluetooth = "";
                            totNbBluetooth = 0;
                        }

                        if(currentTime >= lastBatteryUpdate + 20) {
                            lastBatteryUpdate = currentTime;
                            registerReceiver(mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        }

                        if(currentTime >= lastSensorUpdate + 1) {
                            lastSensorUpdate = currentTime;
                            lastSPSensorData = (lastProximity > -1 ? String.valueOf(lastProximity) : "") + ";" + (lastLight > -1 ? String.valueOf(lastLight) : "") + ";" + (lastAccel > -1 ? String.valueOf(lastAccel) : "")  + ";" + (stepCounterDiff > 0 ? stepCounterDiff:"");
                            stepCounterPre = stepCounter;
                            stepCounterDiff = 0;

                            ServiceSend(String.valueOf(currentTime) + ":" + BatteryLevel + ";" + lastSPSensorData + ";" + (lastAudios > -1 ? lastAudiosEWMA : "") + ";" + lastMotions + ";" + lastMobileInfos + ";" + lastWifiInfos + ";" + lastBluetoothInfos + ";" + lastPositions + ";" + (humanText != "" ? humanText : isHuman));

                            BatteryLevel = "";
                            maxProximity = -1;
                            maxLight = -1;
                            lastAccel = -1;

                            if(humanText != "") {
                                humanText = "";
                            }

                            if(lastAudios > -1) {
                                lastAudiosEWMA = lastAudios * (float) 0.2 + lastAudiosEWMA * (float) 0.8;
                                lastAudios = -1;
                            }

                            lastMobileInfos = ";;;;;";
                            lastWifiInfos = ";";
                            lastBluetoothInfos = ";";
                        }

                        try {
                            Thread.sleep(100);
                        }
                        catch(Exception e) {}
                    }
                }
            };
            mainSave = new Thread(mainSaveCall);
            mainSave.start();
        }
        return Service.START_STICKY;
    }

    protected void ServiceSend(String data) {
        final String tmp_data = data;
        Runnable serviceSendCall = new Runnable() {
            public void run() {
                HttpClient client=new DefaultHttpClient();
                HttpPost getMethod=new HttpPost(liveServiceUrl);
                ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
                nameValuePairs.add(new BasicNameValuePair("data",tmp_data));
                try {
                    getMethod.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8));
                    client.execute(getMethod);
                }
                catch (Exception e) {}
            }
        };
        Thread serviceSend = new Thread(serviceSendCall);
        serviceSend.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Location lastBestLocation = null;
    private void updateLoc(Location loc) {
        if(isBetterLocation(loc, lastBestLocation)) {
            lastBestLocation = loc;
        }

        if(lastBestLocation != null) {
            String altitude = "";
            if(lastBestLocation.hasAltitude())
                altitude = String.valueOf(lastBestLocation.getAltitude());

            String speed = "";
            if(lastBestLocation.hasSpeed())
                speed = String.valueOf(lastBestLocation.getSpeed());

            String bearing = "";
            if(lastBestLocation.hasBearing())
                bearing = String.valueOf(lastBestLocation.getBearing());

            lastPositions = lastBestLocation.getLatitude() + "," + lastBestLocation.getLongitude() + ";" + altitude + ";" +speed + ";" + bearing;
        }
    }

    private static final int ONE_MINUTES = 1000 * 60 * 1;

    /*
        Determines whether one Location reading is better than the current Location fix
        See: http://developer.android.com/guide/topics/location/strategies.html
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > ONE_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -ONE_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    @Override
    public final void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;
        if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) { // Accelerometer without gravity
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            float speed = FloatMath.sqrt(x * x + y * y + z * z);

            if(speed > lastAccel) {
                lastAccel = speed;
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_PROXIMITY) { // Proximity sensor
            if(sensorEvent.values[0] > maxProximity) {
                lastProximity = sensorEvent.values[0];
                maxProximity = sensorEvent.values[0];
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_LIGHT) { // Light
            if(sensorEvent.values[0] > maxLight) {
                lastLight = sensorEvent.values[0];
                maxLight = sensorEvent.values[0];
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_STEP_COUNTER) { // Step counter
            stepCounter = sensorEvent.values[0];
            if(stepCounterPre == -1)
                stepCounterPre = stepCounter;
            stepCounterDiff = stepCounter - stepCounterPre;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public boolean stopService(Intent name) {
        return super.stopService(name);
    }

    @Override
    public void onDestroy() {
        if(mRunning) {
            mRunning = false;
            microRunning = false;
            mSensorManager.unregisterListener(this);
            locationManager.removeUpdates(locationListener);
            mActivityRecognitionScan.stopActivityRecognitionScan();
            unregisterReceiver(updateReceiver);
            wl.release();
            try {
                unregisterReceiver(receiverWifi);
            }
            catch (Exception e) {}
            try {
                unregisterReceiver(BluetoothReceiver);
            }
            catch (Exception e) {}
            try {
                unregisterReceiver(mBatInfoReceiver);
            }
            catch (Exception e) {}

            // #### RecognitionListener
            if (mIsCountDownOn)
                mNoSpeechCountDown.cancel();
            if (mSpeechRecognizer != null)
                mSpeechRecognizer.destroy();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            String wifiData = "";
            List<ScanResult> wifiList = mainWifi.getScanResults();
            int y = 0;
            for(int i = 0; i < wifiList.size(); i++){
                if(!wifiData.contains(wifiList.get(i).SSID)) {
                    if (y > 0)
                        wifiData += ",";
                    wifiData += String.valueOf(wifiList.get(i).SSID) + "(" + String.valueOf(wifiList.get(i).level) + ")";
                    y++;
                }
            }
            lastWifiInfos = wifiData+";"+String.valueOf(y);

            unregisterReceiver(receiverWifi);
        }
    }

    private final BroadcastReceiver BluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getName() != null && !totBluetooth.contains(device.getName())) {
                    if(totNbBluetooth>0)
                        totBluetooth += ",";
                    totBluetooth += device.getName();
                    totNbBluetooth++;
                }
            }
        }
    };

    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            BatteryLevel = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            unregisterReceiver(mBatInfoReceiver);
        }
    };


    /*
        Micro RecognitionListener, thanks to the stackoverflow community
    */
    protected AudioManager mAudioManager;
    protected SpeechRecognizer mSpeechRecognizer;
    protected RecognitionListener mSpeechRecognizerListner;

    protected volatile boolean mIsListening;
    protected volatile boolean mIsCountDownOn;
    private Context m_ctx;

    private void startListening(){
        // turn off beep sound
        mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
        if (!mIsListening)
        {
            recognizeSpeechDirectly();
            mIsListening = true;
        }
    }

    private SpeechRecognizer getSpeechRecognizer()
    {
        if (mSpeechRecognizer == null)
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(m_ctx);
        return mSpeechRecognizer;
    }
    private RecognitionListener getSpeechRecognizerListener()
    {
        if (mSpeechRecognizerListner == null)
            mSpeechRecognizerListner = new SpeechRecognitionListener();
        return mSpeechRecognizerListner;
    }

    private void recognizeSpeechDirectly()
    {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // accept partial results if they come
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizeSpeechDirectly(m_ctx,recognizerIntent, getSpeechRecognizerListener(), getSpeechRecognizer());
    }
    public static void recognizeSpeechDirectly(Context context, Intent recognizerIntent, RecognitionListener listener, SpeechRecognizer recognizer)
    {
        //need to have a calling package for it to work
        if (!recognizerIntent.hasExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE))
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "com.dummy");

        recognizer.setRecognitionListener(listener);
        recognizer.startListening(recognizerIntent);
    }

    public void stop()
    {
        if (getSpeechRecognizer() != null) {
            getSpeechRecognizer().stopListening();
            getSpeechRecognizer().cancel();
            getSpeechRecognizer().destroy();

            mIsListening = false;
            mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
        }
    }

    // Count down timer for Jelly Bean work around
    protected CountDownTimer mNoSpeechCountDown = new CountDownTimer(5000, 5000)
    {
        @Override
        public void onTick(long millisUntilFinished) {}

        @Override
        public void onFinish()
        {
            mIsCountDownOn = false;
            startListening();
        }
    };


    protected class SpeechRecognitionListener implements RecognitionListener
    {
        @Override
        public void onReadyForSpeech(Bundle params)
        {
            mIsCountDownOn = true;
            mNoSpeechCountDown.start();
        }
        @Override
        public void onBeginningOfSpeech()
        {
            isHuman = "1";

            if (mIsCountDownOn) {
                mIsCountDownOn = false;
                mNoSpeechCountDown.cancel();
            }
        }
        @Override
        public void onEndOfSpeech()
        {
            isHuman = "";
        }

        @Override
        public void onBufferReceived(byte[] buffer)
        {

        }

        @Override
        public void onError(int error)
        {
            if ((error == SpeechRecognizer.ERROR_NO_MATCH) || (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                if (mIsCountDownOn) {
                    mIsCountDownOn = false;
                    mNoSpeechCountDown.cancel();
                }
                mIsListening = false;
                startListening();
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params)
        {

        }

        @Override
        public void onPartialResults(Bundle partialResults)
        {

        }

        @Override
        public void onResults(Bundle results)
        {
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if(data.size() >= 1){
                humanText = (String)data.get(0);
            }

            if (mIsCountDownOn)
                mIsCountDownOn = false;
            mIsListening = false;

            startListening();
        }
        @Override
        public void onRmsChanged(float rmsdB)
        {
            if(Math.max(0, rmsdB * 10) > lastAudios) {
                lastAudios = Math.max(0, rmsdB * 10);
            }
        }
    }
}
