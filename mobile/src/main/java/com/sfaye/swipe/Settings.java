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

import java.util.HashMap;
import java.util.Map;

/*
    Provides access to app settings/preferences.
 */
public class Settings {

    private volatile Map<String, Integer> table;
    private volatile Map<String, String> tableStr;

    public Settings() {
        table = new HashMap<String, Integer>();
        tableStr = new HashMap<String, String>();

        // Integers
        // ***
        table.put("A_INTERVAL", 30); // Accelerometer recording interval (seconds)
        table.put("SC_INTERVAL", 60); // Steps recording interval (seconds)
        table.put("B_INTERVAL", 60); // Battery recording interval (seconds)
        table.put("GPS_INTERVAL", 60); // GPS recording interval (seconds)
        table.put("BL_INTERVAL", 120); // Bluetooth recording interval (seconds)
        table.put("WI_INTERVAL", 300); // WiFi recording interval (seconds)
        table.put("CE_INTERVAL", 300); // Cell recording interval (seconds)
        table.put("AU_INTERVAL", 60); // Audio recording interval (seconds)
        table.put("AR_INTERVAL", 60); // Activity recording interval (seconds) - 0 = as fast as possible
        // ***
        table.put("MIN_BATTERY", 5); // Minimum battery to start the application (%)
        // ***
        table.put("AUTO_START_SERVICE", 0); // Automatically starts the data collection when the application is launched (1 = yes, 0 = no)
        table.put("AUTO_START_APPLICATION", 0); // Automatically launch the application when the smartphone is started (1 = yes, 0 = no)
        table.put("NIGHT_STOP", 0); // Disable the data collection between 00:00 am and 05:00 am (1 = yes, 0 = no)

        // Strings
        // ***
        tableStr.put("SERVICE_URL", "http://server/service_upload.php"); // Web service
    }

    synchronized public int getInt(String name) {
        return (int) table.get(name);
    }

    synchronized public String getStr(String name) {
        return tableStr.get(name);
    }

    synchronized public void setInt(String name, int val) {
        table.put(name, new Integer(val));
    }

    synchronized public void setStr(String name, String val) {
        tableStr.put(name, val);
    }
}