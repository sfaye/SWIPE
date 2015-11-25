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

import android.app.Activity;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.location.LocationManager;
import android.net.Uri;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.content.Context;
import java.io.*;
import android.view.View;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import android.content.Intent;
import com.google.android.gms.wearable.Node;

import android.app.ActivityManager.RunningServiceInfo;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

/*
    Main activity of the smartphone application. It shows different options to launch the data collection and manages the link with:
        * the smartwatch (get data from the smartwatch)
        * SensorService (get data from the sensors of the smartphone)
        * the web service (send all the data to record it on a web server)
*/
public class MainActivity extends Activity implements MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    Node mNode;
    private TextView info;
    private TextView infoProfile;
    private TextView notConnected;
    Context thisContext = null;
    private Button buttonStart;
    private Button buttonStop;
    private ProgressBar sendServiceProgressBar;
    private ProgressBar progressBarWhenStarted;
    private volatile Hashtable<Integer, List<String>> BDDwearable;
    private volatile long curTime = 0;
    private volatile String lastSensorData = null;
    private volatile long initialTimestamp = 0;
    private GoogleApiClient mGoogleApiClient;
    private static final String START_WEAR = "/start-wear";
    private static final String STOP_WEAR = "/stop-wear";
    private PowerManager.WakeLock wl;
    public volatile boolean isStarted = false;
    public volatile String currentProfile;
    public volatile long lastStartStop = 0;
    private Intent intent = null;
    private Context gContext = null;
    private volatile Thread verifIsConnected;
    private volatile boolean verifIsConnectedThread;
    private volatile int lastBattery = -1;
    private volatile long lastMessageFromWear = 0;
    private volatile Settings settings;
    private volatile boolean isWatchVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        settings = new Settings();
        gContext = getApplicationContext();
        thisContext = getBaseContext();

        currentProfile = "TEST";
        try {
            File file = new File(getFilesDir() + "/config_profile");
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                if(line.length() >= 2)
                    currentProfile = line;
                break;
            }
            br.close();
        }
        catch (IOException e) {}

        if(currentProfile.equals("TEST") && isServiceRunning(gContext) == 0) {
            Toast.makeText(thisContext, "First launch: please set your profile name.", Toast.LENGTH_SHORT).show();
        }

        verifIsConnectedThread = true;
        verifIsConnected = new Thread(new Runnable() {
            public void run() {
                while(verifIsConnectedThread) {
                    // Node
                    if(!isStarted) {
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                            @Override
                            public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                                isWatchVisible = false;
                                for (Node node : nodes.getNodes()) {
                                    isWatchVisible = true;
                                    break;
                                }

                                if (isWatchVisible) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            notConnected.setVisibility(View.INVISIBLE);
                                        }
                                    });
                                }
                                else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            notConnected.setVisibility(View.VISIBLE);
                                        }
                                    });
                                }
                            }
                        });
                    }

                    // Stop if wear not there / no battery
                    if(isStarted && lastBattery > 0 && lastBattery < settings.getInt("MIN_BATTERY") && (System.currentTimeMillis() / 1000) - 60 > lastStartStop && (System.currentTimeMillis() / 1000) - 4*60 > lastMessageFromWear) {
                        startStopWear(true);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Smartwatch alert")
                                    .setMessage("No more battery! Your session was recorded and stopped.")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {}
                                    })
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                            }
                        });
                    }

                    // Stop if message and if hour too late
                    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    if(isStarted && (settings.getInt("NIGHT_STOP") == 1 && (hour >= 0 && hour <= 5)) && (System.currentTimeMillis() / 1000) - 60 > lastStartStop  && (System.currentTimeMillis() / 1000) - 60 < lastMessageFromWear) {
                        startStopWear(true);
                    }

                    try {
                        Thread.sleep(5000);
                    }
                    catch (Exception e) {}
                }
            }
        });

        sendServiceProgressBar = (ProgressBar) findViewById(R.id.sendServiceProgressBar);
        sendServiceProgressBar.setVisibility(View.INVISIBLE);
        progressBarWhenStarted = (ProgressBar) findViewById(R.id.progressBarWhenStarted);
        progressBarWhenStarted.setVisibility(View.INVISIBLE);

        initialTimestamp = System.currentTimeMillis() / 1000;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();

        mGoogleApiClient.connect();

        infoProfile = (TextView) findViewById(R.id.currentProfile);
        infoProfile.setText("Current profile: " + currentProfile);
        info = (TextView) findViewById(R.id.info);
        notConnected = (TextView) findViewById(R.id.notConnected);

        BDDwearable = new Hashtable();

        buttonStart = (Button) findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                startStopWear(false);
            }
        });

        buttonStop = (Button) findViewById(R.id.buttonStop);
        buttonStop.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                startStopWear(false);
            }
        });

        // Init
        if(isServiceRunning(gContext) > 0) {
            isStarted = true;
            buttonStart.setVisibility(View.INVISIBLE);
            buttonStop.setVisibility(View.VISIBLE);
            info.setVisibility(View.VISIBLE);
            notConnected.setVisibility(View.INVISIBLE);
            progressBarWhenStarted.setVisibility(View.VISIBLE);
        }
        else {
            // Activates GPS
            LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            boolean gps_enabled = false;
            boolean network_enabled = false;
            try{
                gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            }catch(Exception ex){}
            try{
                network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }catch(Exception ex){}

            if(!gps_enabled && !network_enabled){
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setMessage("You need to activate GPS ressources" );
                dialog.setPositiveButton("Setting", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        // TODO Auto-generated method stub
                        startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS), 100);
                    }
                });
                dialog.show();
            }

            // Launch service if a profile is defined and if it is in the settings
            boolean isRunningCreate = false;
            if(settings.getInt("AUTO_START_SERVICE") == 1 && isWatchVisible && !currentProfile.equals("TEST") && isServiceRunning(gContext) == 0) {
                Toast.makeText(thisContext, "Please wait 5 seconds and go back to home screen", Toast.LENGTH_SHORT).show();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(5000);
                        }
                        catch(Exception e) {}

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startStopWear(false);
                            }
                        });

                    }
                }).start();
                isRunningCreate = true;
            }

            isStarted = false;
            if(isRunningCreate) {
                buttonStart.setVisibility(View.INVISIBLE);
                buttonStop.setVisibility(View.INVISIBLE);
                info.setVisibility(View.INVISIBLE);
                sendServiceProgressBar.setVisibility(View.VISIBLE);
                progressBarWhenStarted.setVisibility(View.INVISIBLE);
            }
            else {
                buttonStart.setVisibility(View.VISIBLE);
                buttonStop.setVisibility(View.INVISIBLE);
                info.setVisibility(View.INVISIBLE);
                notConnected.setVisibility(View.INVISIBLE);
            }
        }

        if(!verifIsConnected.isAlive())
            verifIsConnected.start();

        // Get the Wake Lock
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wlTag");
        wl.acquire();
    }

    /**
     * Send message to mobile handheld
     */
    private void startStopWear(final boolean discret) {
        lastStartStop = System.currentTimeMillis() / 1000;

        intent = null;
        intent = new Intent(gContext, SensorService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        String wearPath = (isStarted ? STOP_WEAR : START_WEAR);

        if (mNode != null && mGoogleApiClient!=null && mGoogleApiClient.isConnected()) {
            Wearable.MessageApi.sendMessage(

                    mGoogleApiClient, mNode.getId(), wearPath, null).setResultCallback(

                    new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {

                            /*if (!sendMessageResult.getStatus().isSuccess() && !discret) {
                                Toast.makeText(thisContext, "Error: failed to start the smartwatch (not connected?), please retry!", Toast.LENGTH_SHORT).show();
                            }
                            else {*/
                                isStarted = !isStarted;
                                if(isStarted) { // Start the service
                                    if(isServiceRunning(gContext) == 0) {
                                        delFile("main_sgl"); delFile("main_sp");
                                        startService(intent);
                                    }
                                    buttonStart.setVisibility(View.INVISIBLE);
                                    buttonStop.setVisibility(View.VISIBLE);
                                    info.setVisibility(View.VISIBLE);
                                    progressBarWhenStarted.setVisibility(View.VISIBLE);
                                    sendServiceProgressBar.setVisibility(View.INVISIBLE);
                                    initialTimestamp = System.currentTimeMillis() / 1000;
                                }
                                else { // Stop the service

                                    // Loading bar
                                    if(isServiceRunning(gContext) > 0) {
                                        stopService(intent);
                                    }
                                    sendServiceProgressBar.setVisibility(View.VISIBLE);
                                    buttonStart.setVisibility(View.INVISIBLE);
                                    buttonStop.setVisibility(View.INVISIBLE);
                                    info.setVisibility(View.INVISIBLE);
                                    progressBarWhenStarted.setVisibility(View.INVISIBLE);

                                    // Send data to the service
                                    Runnable serviceSendCall = new Runnable() {
                                        public void run() {
                                            if(serviceUpload("main_sgl") && serviceUpload("main_sp")) {
                                                // End loading
                                            }
                                            else {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        new AlertDialog.Builder(MainActivity.this)
                                                                .setTitle("Unable to send data")
                                                                .setMessage("SWIPE is not able to send your data through WiFi or Cellular network. However, as it is recorded, you can send it later. Do not restart a new session in the meantime.")
                                                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                                                    public void onClick(DialogInterface dialog, int which) {}
                                                                })
                                                                .setIcon(android.R.drawable.ic_dialog_alert)
                                                                .show();
                                                    }
                                                });

                                                /*runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if(!discret)
                                                            Toast.makeText(thisContext, "Error: please enable WiFi or cell", Toast.LENGTH_SHORT).show();
                                                        sendServiceProgressBar.setVisibility(View.INVISIBLE);
                                                    }
                                                });
                                                startStopWear(true);*/
                                            }

                                            BDDwearable.clear();
                                            initialTimestamp = System.currentTimeMillis() / 1000;

                                            // End loading
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if(!discret)
                                                        Toast.makeText(thisContext, "That's all folks!", Toast.LENGTH_SHORT).show();
                                                    buttonStart.setVisibility(View.VISIBLE);
                                                    sendServiceProgressBar.setVisibility(View.INVISIBLE);
                                                }
                                            });
                                        }
                                    };
                                    Thread serviceSend = new Thread(serviceSendCall);
                                    serviceSend.start();
                                }
                            //}
                        }
                    }
            );
        }else{}
    }

    protected void delFile(String fileName) {
        try {
            File file = new File(getFilesDir() + "/" + fileName); // new File(Environment.getExternalStorageDirectory(), "Test");
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            file.setReadable(true, false);
        } catch (Exception e) {}
    }

    // Thanks to the stackoverflow community
    protected boolean serviceUpload(String fileName) {
        boolean isOK = true;
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;
        String urlServer = settings.getStr("SERVICE_URL");
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "*****";

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1*1024*1024;

        try
        {
            FileInputStream fileInputStream = new FileInputStream( new File(getFilesDir() + "/" + fileName) );

            URL url = new URL(urlServer);
            connection = (HttpURLConnection) url.openConnection();

            // Allow Inputs &amp; Outputs.
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            // Set HTTP method to POST.
            connection.setRequestMethod("POST");

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

            outputStream = new DataOutputStream( connection.getOutputStream() );
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\""+currentProfile + "_" + fileName+"\"" + lineEnd);
            outputStream.writeBytes(lineEnd);

            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // Read file
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0)
            {
                outputStream.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }

            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            fileInputStream.close();
            outputStream.flush();
            outputStream.close();

            if(connection.getResponseCode() != 200)
                isOK = false;
        }
        catch (Exception ex) { isOK = false; }

        return isOK;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public void Maj() {
        try {
            File file = new File(getFilesDir() + "/main_sgl"); // new File(Environment.getExternalStorageDirectory(), "TestFolder");
            if(!file.exists()) {
                file.createNewFile();
                file.setReadable(true, false);
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(ReadSettings(BDDwearable));
            bw.close();
        }
        catch (Exception e) {
            //
        }

        BDDwearable.clear();
    }

    public String ReadSettings(Hashtable<Integer, List<String>> BDDtoread){

        String data = "";
        int str = 0;

        Set<Integer> set = BDDtoread.keySet();
        Iterator<Integer> itr = set.iterator();
        while (itr.hasNext()) {
            str = itr.next();
            data = data + Integer.toString(str) + ";" + BDDtoread.get(str).get(0) + "\n";
        }

        return data;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy(); verifIsConnectedThread = false; wl.release();
    }

    // Resolve the node = the connected device to send the message to
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

    public static int isServiceRunning(Context context){
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
        for (RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(SensorService.class.getName())){
                return 1;
            }
        }
        return 0;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        resolveNode();
    }

    @Override
    public void onConnectionSuspended(int i) {
        //
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            LayoutInflater li = LayoutInflater.from(thisContext);
            View promptsView = li.inflate(R.layout.prompt_userconfig, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
            alertDialogBuilder.setView(promptsView);
            final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextProfileConfig);
            userInput.setText(currentProfile);

            alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            currentProfile = userInput.getText().toString().toUpperCase();
                            delFile("config_profile");
                            try {
                                File file = new File(getFilesDir() + "/config_profile");
                                FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
                                BufferedWriter bw = new BufferedWriter(fw);
                                bw.write(currentProfile);
                                bw.close();
                            }
                            catch (Exception e) {}

                            infoProfile.setText("Current profile: " + currentProfile);
                        }
                    })
                .setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
        else if (id == R.id.action_reuploadService) {
            // Send data to the service
            Runnable serviceSendCall = new Runnable() {
                public void run() {
                    if(serviceUpload("main_sgl") && serviceUpload("main_sp")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(thisContext, "OK!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Unable to send data")
                                .setMessage("SWIPE is not able to send your data through WiFi or Cellular network. However, as it is recorded, you can send it later. Do not restart a new session in the meantime.")
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {}
                                })
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                            }
                        });
                    }
                }
            };
            Thread serviceSend = new Thread(serviceSendCall);
            serviceSend.start();
        }
        else if (id == R.id.action_reuploadMail) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE, Uri.fromParts("mailto", "m@sfaye.com", null));
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "m@sfaye.com" });
            intent.putExtra(Intent.EXTRA_SUBJECT, "SWIPE data");
            intent.putExtra(Intent.EXTRA_TEXT, "FYI");
            ArrayList<Uri> uris = new ArrayList<Uri>();
            uris.add(Uri.parse("file://" + getFilesDir() + "/main_sgl"));
            uris.add(Uri.parse("file://" + getFilesDir() + "/main_sp"));
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, "Send Email"));
        }
        return super.onOptionsItemSelected(item);
    }

    // MessageApi
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals("/wear-collect")) {
            lastMessageFromWear = (System.currentTimeMillis() / 1000);
            String data = new String(messageEvent.getData());
            int minCurTime = 0;
            try {
                String[] split = data.split(";");
                for (int i = 0; i < split.length; i++) {
                    String[] splitBis = split[i].split(":");

                    // Update
                    List<String> tmp = new ArrayList<String>();
                    int tmpCurTime = Integer.parseInt(splitBis[1]);

                    if(tmpCurTime > initialTimestamp) {
                        lastSensorData = splitBis[0].replace(",", ";");
                        if(tmpCurTime > minCurTime) {
                            String[] splitBisBis = lastSensorData.split(";");
                            if(splitBisBis[0].length() > 0) {
                                lastBattery = Integer.parseInt(splitBisBis[0]);
                                if(lastBattery <= 0)
                                    lastBattery = 1;
                            }
                        }

                        tmp.add(lastSensorData);
                        BDDwearable.put(tmpCurTime, tmp);
                    }
                }
            }
            catch (Exception e) {}

            curTime = System.currentTimeMillis() / 1000;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    info.setText("Last sync. (smartwatch): " + String.valueOf(curTime - initialTimestamp));
                }
            });

            Maj();
        }
    }
}
