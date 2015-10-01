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

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import android.app.IntentService;
import android.content.Intent;

import java.util.List;

/*
    Get last recognized activity
    About Android Activity Recognition:
        http://opensignal.com/blog/2013/05/16/getting-started-with-activity-recognition-android-developer-guide/
        https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognition
*/
public class ActivityRecognitionService extends IntentService{

    private int lastActivity = -1;
    private int lastActivityConfidence = -1;

    public ActivityRecognitionService() {
        super("ActivityRecognitionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();

            if (mostProbableActivity.getType() == DetectedActivity.ON_FOOT) {
                DetectedActivity betterActivity = walkingOrRunning(result.getProbableActivities());
                if (betterActivity != null)
                    mostProbableActivity = betterActivity;
            }

            lastActivity = mostProbableActivity.getType();
            lastActivityConfidence = result.getMostProbableActivity().getConfidence();

            // Sends current result
            Intent intentUp = new Intent("updatedActivity");
            intentUp.putExtra("activity", (lastActivity > -1 ? String.valueOf(lastActivity) : "") + ";" + (lastActivityConfidence > -1 ? String.valueOf(lastActivityConfidence) : ""));
            sendBroadcast(intentUp);
        }
    }

    private DetectedActivity walkingOrRunning(List<DetectedActivity> probableActivities) {
        DetectedActivity myActivity = null;
        int confidence = 0;
        for (DetectedActivity activity : probableActivities) {
            if (activity.getType() != DetectedActivity.RUNNING && activity.getType() != DetectedActivity.WALKING)
                continue;

            if (activity.getConfidence() > confidence)
                myActivity = activity;
        }

        return myActivity;
    }

    private static String getFriendlyName(int detected_activity_type){
        switch (detected_activity_type ) {
            case DetectedActivity.IN_VEHICLE: // 0
                return "in vehicle";
            case DetectedActivity.ON_BICYCLE: // 1
                return "on bike";
            case DetectedActivity.ON_FOOT: // 2
                return "on foot";
            case DetectedActivity.RUNNING: // 3
                return "running";
            case DetectedActivity.STILL: // 4
                return "still";
            case DetectedActivity.TILTING: // 5
                return "tilting";
            case DetectedActivity.WALKING: // 6
                return "walking";

            default:
                return "unknown";
        }
    }
}