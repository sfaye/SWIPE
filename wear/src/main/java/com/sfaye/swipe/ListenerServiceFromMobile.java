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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/*
    Start or stop the application on the smartwatch depending on messages received from the smartphone
 */
public class ListenerServiceFromMobile extends WearableListenerService {

    private static final String START_WEAR = "/start-wear";
    private static final String STOP_WEAR = "/stop-wear";
    private static final String START_WEAR_LIVE = "/start-wear-live";
    private static final String STOP_WEAR_LIVE = "/stop-wear-live";
    private Intent intent = null;
    private Context gContext = null;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        gContext = getApplicationContext();

        Intent startIntent = new Intent(this, MainActivity.class);
        startIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        intent = null;
        if (messageEvent.getPath().equals(START_WEAR_LIVE) || messageEvent.getPath().equals(STOP_WEAR_LIVE))
            intent = new Intent(gContext, SensorServiceLive.class);
        else
            intent = new Intent(gContext, SensorService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Receive the message from mobile
        if (messageEvent.getPath().equals(START_WEAR) || messageEvent.getPath().equals(START_WEAR_LIVE)) {
            if(!isServiceRunning(gContext)) {
                gContext.startService(intent);
            }
        }
        else if (messageEvent.getPath().equals(STOP_WEAR) || messageEvent.getPath().equals(STOP_WEAR_LIVE)) {
            if(isServiceRunning(gContext)) {
                gContext.stopService(intent);
            }
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "TAG");
        wl.acquire(1000);
        startActivity(startIntent);
    }

    public static boolean isServiceRunning(Context context){
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(SensorService.class.getName()) || runningServiceInfo.service.getClassName().equals(SensorServiceLive.class.getName())){
                return true;
            }
        }
        return false;
    }
}
