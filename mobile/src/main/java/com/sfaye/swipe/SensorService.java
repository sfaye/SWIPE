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
import android.bluetooth.BluetoothManager;
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
import android.media.MediaRecorder;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.FloatMath;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/*
    Get data from the sensors with the normal mode (one day data collection).
 */
public class SensorService extends Service implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public LocationManager locationManager;
    Context thisContext = null;
    private volatile Hashtable<Integer, List<String>> BDD;
    private volatile String lastPositions = ";;;";
    private volatile String lastSPSensorData = ";;;;";
    private volatile String lastMobileInfos = ";;;;;";
    private volatile String lastWifiInfos = ";";
    private volatile String lastBluetoothInfos = ";";
    private volatile String lastBLEBluetoothInfos = ";";
    private volatile String lastMotions = ";";
    private volatile String BatteryLevel = "";
    private volatile float totAudios = 0;
    private volatile float sumAudios = 0;
    private GoogleApiClient mGoogleApiClient;
    public LocationListener locationListener;
    private volatile long lastUpdate = 0;
    private volatile long curTime = 0;
    private SensorManager mSensorManager;
    private volatile long lastSensorUpdate = 0;
    private volatile long lastWifiUpdate = 0;
    private volatile long lastBatteryUpdate = 0;
    private volatile long lastBluetoothUpdate = 0;
    private volatile long lastMobileInfosUpdate = 0;
    private Sensor mProximitySensor;
    private Sensor mLightSensor;
    private Sensor mLinearAccelerationSensor;
    private volatile long lastAccelTime = 0;
    private volatile long lastAudioTime = 0;
    private volatile Sensor mStepCounterSensor;
    private volatile float lastProximity = -1;
    private volatile float lastLight = -1;
    private volatile float lastAccel = -1;
    private volatile long firstStepCounter = -1;
    private volatile float lastAccelTot = 0;
    private volatile float lastAccelAvg = 0;
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
    private Handler BLEhandler = new Handler();
    private volatile String totLTEBluetooth = "";
    private volatile int totNbLTEBluetooth = 0;
    private volatile boolean microRunning = true;
    Thread recordMicroThread;
    private volatile boolean mRunning;
    BluetoothAdapter bluetoothAdapter;
    TelephonyManager teleMan;
    Thread mainSave;
    private volatile boolean isGPSactivated = false;
    private volatile Settings settings;

    // ###################
    // ###    Init    ###
    // ###################
    @Override
    public void onCreate() {
        // ***
        super.onCreate();
        thisContext = getBaseContext();
        BDD = new Hashtable();
        settings = new Settings();

        // ***
        teleMan = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE); // Mobile network
        mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE); // Wifi
        receiverWifi = new WifiReceiver();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // Bluetooth
        mGoogleApiClient = new GoogleApiClient.Builder(this) // Google Services
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        // Location
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if(location != null)
                    updateLoc(location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        // Micro
        Runnable recordMicroCall = new Runnable() {
            public void run() {
                recordMicro();
            }
        };
        recordMicroThread = new Thread(recordMicroCall);
        recordMicroThread.start();

        // Sensors
        mSensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        mLinearAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mStepCounterSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if(!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }

        if(!mainWifi.isWifiEnabled()) {
            mainWifi.setWifiEnabled(true);
        }

        // Get the Wake Lock
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wlTag");
    }

    public void goGPS() {
        try {
            locationManager.removeUpdates(locationListener);
        }
        catch (Exception e) {}
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, settings.getInt("GPS_INTERVAL") * 1000, 0, locationListener);
                isGPSactivated = true;
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, settings.getInt("GPS_INTERVAL") * 1000, 0, locationListener);
                isGPSactivated = true;
            } else {
                isGPSactivated = false;
            }
        } catch (Exception e) {}
    }

    // ###################
    // ###    Start    ###
    // ###################
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mRunning) {
            // ***
            mRunning = true;
            wl.acquire();

            // ***
            mSensorManager.registerListener(this, this.mLinearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, this.mStepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
            goGPS();

            // Activity manager
            mActivityRecognitionScan.startActivityRecognitionScan();
            updateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    lastMotions = intent.getExtras().getString("activity");

                    /*
                    Example: adaptive sampling rate on the smartphone based on current activity
                    String[] parts = lastMotions.split(";");
                    if(parts[0].equals("3")) { // Still
                        settings.setInt(..., ...);
                    }
                    else { // Rest - Walking, tilting, etc.
                        settings.setInt(..., ...);
                    }
                    */

                    // GPS?
                    if(!isGPSactivated) {
                        goGPS();
                    }
                }
            };
            IntentFilter updateIntentFilter = new IntentFilter("updatedActivity");
            registerReceiver(updateReceiver, updateIntentFilter);

            // ###################
            // ### Main thread ###
            // ###################
            Runnable mainSaveCall = new Runnable() {
                public void run() {
                    while(mRunning) {
                        // ***
                        long currentTime = System.currentTimeMillis() / 1000;

                        // Mobile network
                        if(currentTime >= lastMobileInfosUpdate + settings.getInt("CE_INTERVAL")) {
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

                        // Wifi
                        if(currentTime >= lastWifiUpdate + settings.getInt("WI_INTERVAL")) {
                            lastWifiUpdate = currentTime;
                            try {
                                registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                                mainWifi.startScan();
                            }
                            catch (Exception e) {}
                        }

                        // Bluetooth
                        if(!isBluetoothScan && currentTime >= lastBluetoothUpdate + settings.getInt("BL_INTERVAL")) {
                            try {
                                //registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                                bluetoothAdapter.startDiscovery();
                                isBluetoothScan = true;
                                lastBluetoothUpdate = currentTime;
                            }
                            catch (Exception e) {}
                        }

                        // Battery
                        if(currentTime >= lastBatteryUpdate + settings.getInt("B_INTERVAL")) {
                            lastBatteryUpdate = currentTime;
                            registerReceiver(mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        }

                        // Sensors
                        if(currentTime > lastSensorUpdate) {
                            lastSensorUpdate = currentTime;

                            // Acceleration
                            boolean recordAcceleration = false;
                            if(lastAccel > -1 && currentTime - lastAccelTime >= settings.getInt("A_INTERVAL"))  {
                                lastAccelTime = currentTime;
                                recordAcceleration = true;
                            }

                            // Steps
                            boolean recordStepCounter = false;
                            if(stepCounterDiff > 0 && currentTime - firstStepCounter >= settings.getInt("SC_INTERVAL")) {
                                firstStepCounter = -1;
                                recordStepCounter = true;
                            }

                            // Audio
                            boolean recordAudio = false;
                            if(totAudios > 0 && currentTime - lastAudioTime >= settings.getInt("AU_INTERVAL")) {
                                lastAudioTime = currentTime;
                                recordAudio = true;
                            }

                            // All sensors
                            lastSPSensorData = (maxProximity > -1 ? String.valueOf(maxProximity) : "") + ";" + (maxLight > -1 ? String.valueOf(maxLight) : "") + ";" + (recordAcceleration ? String.valueOf((float) Math.round(lastAccel*10)/10.0) : "") + ";" + (recordAcceleration && lastAccelTot > 0 ? ((float) Math.round((lastAccelAvg/lastAccelTot)*10)/10.0):"") + ";" + (recordStepCounter ? stepCounterDiff:"");

                            // Is there something to record?
                            if(!lastSPSensorData.equals(";;;;") || !BatteryLevel.equals("") || !lastMobileInfos.equals(";;;;;")
                                    || !lastWifiInfos.equals(";") || !lastBluetoothInfos.equals(";") || !lastBLEBluetoothInfos.equals(";")
                                    || !lastPositions.equals(";;;") || !lastMotions.equals(";") || recordAudio) {

                                // ***
                                List<String> tmp = new ArrayList<String>();
                                tmp.add(BatteryLevel);
                                BatteryLevel = "";
                                tmp.add(lastSPSensorData);
                                lastSPSensorData = ";;;;";
                                maxProximity = -1;
                                maxLight = -1;
                                if(recordAcceleration) {
                                    lastAccel = -1;
                                    lastAccelTot = 0;
                                    lastAccelAvg = 0;
                                }
                                if(recordStepCounter) {
                                    stepCounterPre = stepCounter;
                                    stepCounterDiff = 0;
                                }
                                tmp.add(recordAudio ? String.valueOf(sumAudios/totAudios) : "");
                                if(recordAudio) {
                                    totAudios = 0;
                                    sumAudios = 0;
                                }
                                tmp.add(lastMotions);
                                lastMotions = ";";
                                tmp.add(lastMobileInfos);
                                lastMobileInfos = ";;;;;";
                                tmp.add(lastWifiInfos);
                                lastWifiInfos = ";";
                                tmp.add(lastBluetoothInfos);
                                lastBluetoothInfos = ";";
                                tmp.add(lastBLEBluetoothInfos);
                                lastBLEBluetoothInfos = ";";
                                tmp.add(lastPositions);
                                lastPositions = ";;;";

                                // ***
                                BDD.put((int) currentTime, tmp);
                            }
                        }

                        curTime = System.currentTimeMillis() / 1000;
                        if(lastUpdate + 300 < curTime)
                            Maj();

                        try {
                            Thread.sleep(1000);
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

    // ################
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scanBLE() {
        final BluetoothAdapter adapter = getBluetoothAdapter();
        BLEhandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adapter.stopLeScan(callback);
                lastBLEBluetoothInfos = totLTEBluetooth + ";" + String.valueOf(totNbLTEBluetooth);
                totLTEBluetooth = "";
                totNbLTEBluetooth = 0;
            }
        }, 10000);
        adapter.startLeScan(callback);
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return manager.getAdapter();
    }

    private final BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if(device.getName() != null && !totLTEBluetooth.contains(device.getAddress())) {
                if (totNbLTEBluetooth > 0)
                    totLTEBluetooth += ",";
                totLTEBluetooth += device.getName() + "!" + device.getAddress();
                totNbLTEBluetooth++;
            }
        }
    };

    private void recordMicro() {
        MediaRecorder recorder=new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile("/dev/null");

        boolean microOK = false;
        try {
            recorder.prepare();
            recorder.start();
            recorder.getMaxAmplitude();
            microOK = true;
        }
        catch (Exception e) {
            microRunning = false;
        }

        while(microRunning)
        {
            float maxampl = recorder.getMaxAmplitude();

            totAudios++;
            sumAudios += maxampl;

            try {
                Thread.sleep(1000);
            }
            catch (Exception e) {}
        }

        if(microOK) {
            recorder.stop();
            recorder.release();
        }
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
            lastAccelAvg += speed;
            lastAccelTot += 1;

            if(speed > lastAccel) {
                lastAccel = speed;
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_PROXIMITY) { // Proximity sensor
            if(sensorEvent.values[0] > maxProximity && sensorEvent.values[0] != lastProximity) {
                lastProximity = sensorEvent.values[0];
                maxProximity = sensorEvent.values[0];
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_LIGHT) { // Light
            if(sensorEvent.values[0] > maxLight && sensorEvent.values[0] != lastLight) {
                lastLight = sensorEvent.values[0];
                maxLight = sensorEvent.values[0];
            }
        }
        else if (mySensor.getType() == Sensor.TYPE_STEP_COUNTER) { // Step counter
            long currentTime = System.currentTimeMillis() / 1000;

            stepCounter = (int) sensorEvent.values[0];
            if(firstStepCounter == -1)
                firstStepCounter = currentTime;
            if(stepCounterPre == -1)
                stepCounterPre = stepCounter;
            stepCounterDiff = stepCounter - stepCounterPre;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    public void Maj() {
        lastUpdate = curTime;
        try {
            File file = new File(getFilesDir() + "/main_sp"); // new File(Environment.getExternalStorageDirectory(), "test"); ?
            if(!file.exists()) {
                file.createNewFile();
                file.setReadable(true, false);
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(ReadSettings(BDD, thisContext, 0));
            bw.close();
        }
        catch (Exception e) {}

        BDD.clear();
    }

    public String ReadSettings(Hashtable<Integer, List<String>> BDDtoread, Context context, int tornot){

        String data = "";
        int str = 0;

        Set<Integer> set = BDDtoread.keySet();
        Iterator<Integer> itr = set.iterator();
        while (itr.hasNext()) {
            str = itr.next();
            data = data + Integer.toString(str) + ";" + BDDtoread.get(str).get(0) + ";" + BDDtoread.get(str).get(1) + ";" + BDDtoread.get(str).get(2) + ";" + BDDtoread.get(str).get(3) + ";" + BDDtoread.get(str).get(4) + ";" + BDDtoread.get(str).get(5) + ";" + BDDtoread.get(str).get(6) + ";" + BDDtoread.get(str).get(7) + ";" + BDDtoread.get(str).get(8) + "\n";
        }

        if (tornot == 1)
            Toast.makeText(context, " " + data, Toast.LENGTH_SHORT).show();

        return data;
    }

    @Override
    public boolean stopService(Intent name) {
        return super.stopService(name);
    }

    @Override
    public void onDestroy() {
        if(mRunning) {
            Maj();
            mRunning = false;
            microRunning = false;
            mSensorManager.unregisterListener(this);
            locationManager.removeUpdates(locationListener);
            mActivityRecognitionScan.stopActivityRecognitionScan();
            unregisterReceiver(updateReceiver);
            wl.release();
            BDD.clear();
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
                if(!wifiData.contains(wifiList.get(i).BSSID)) {
                    if (y > 0)
                        wifiData += ",";
                    wifiData += String.valueOf(wifiList.get(i).SSID) + "!" + String.valueOf(wifiList.get(i).BSSID) + "(" + String.valueOf(wifiList.get(i).level) + ")";
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
                if(device.getName() != null && !totBluetooth.contains(device.getAddress())) {
                    if(totNbBluetooth>0)
                        totBluetooth += ",";
                    totBluetooth += device.getName() + "!" + device.getAddress();
                    totNbBluetooth++;
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                isBluetoothScan = false;
                lastBluetoothInfos = totBluetooth + ";" + String.valueOf(totNbBluetooth);
                totBluetooth = "";
                totNbBluetooth = 0;
                scanBLE();
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
}
