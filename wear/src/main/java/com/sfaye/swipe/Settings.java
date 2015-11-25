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
        table.put("MIN_HR_INTERVAL", 60); // Minimum heart rate recording interval (seconds)
        table.put("MAX_HR_INTERVAL", 300); // Maximum heart rate recording interval (seconds)
        // ***
        table.put("MIN_BATTERY", 5); // Minimum battery to start the application (%)
        table.put("TIMEOUT_SYNC", 60); // Sync. with the smartphone (seconds). If > 60 seconds, the Bluetooth of the smartwatch is automatically switched off and on. // 1325
        table.put("NIGHT_STOP", 0); // Disable the data collection between 00:00 am and 05:00 am ? (1 = yes, 0 = no)
        // ***

        // Strings
        // ***
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