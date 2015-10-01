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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.ActivityRecognitionClient;

/*
    Manage the activity recognition service
    About Android Activity Recognition:
        http://opensignal.com/blog/2013/05/16/getting-started-with-activity-recognition-android-developer-guide/
        https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognition
 */
public class ActivityRecognitionScan implements GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener {
    private Context context;
    private static ActivityRecognitionClient mActivityRecognitionClient;
    private static PendingIntent callbackIntent;

    public ActivityRecognitionScan(Context context) {
        this.context=context;
    }

    public void startActivityRecognitionScan(){
        mActivityRecognitionClient = new ActivityRecognitionClient(context, this, this);
        mActivityRecognitionClient.connect();
    }

    public void stopActivityRecognitionScan(){
        try{
            mActivityRecognitionClient.removeActivityUpdates(callbackIntent);
        }
        catch (IllegalStateException e){
           //
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        //
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Intent intent = new Intent(context, com.sfaye.swipe.ActivityRecognitionService.class);
        callbackIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mActivityRecognitionClient.requestActivityUpdates(0, callbackIntent); // 0 = as fast as possible
    }

    @Override
    public void onDisconnected() {
    }
}