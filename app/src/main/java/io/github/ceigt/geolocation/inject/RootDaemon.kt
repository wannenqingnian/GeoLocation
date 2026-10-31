package io.github.ceigt.geolocation.inject

import android.os.Looper
import android.util.Log

object RootDaemon {
    private const val TAG = "GeoLocationRootDaemon"

    @JvmStatic
    fun main(args: Array<String>) {
        Log.i(TAG, "Starting GeoLocation Root Daemon...")
        try {
            Looper.prepareMainLooper()
        } catch (t: Throwable) {
            Log.w(TAG, "prepareMainLooper failed or already prepared: ${t.message}")
        }

        try {
            RootEntry.init()
            Log.i(TAG, "GeoLocation Root Daemon initialized successfully. Entering loop...")
            Looper.loop()
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error in GeoLocation Root Daemon: ${t.message}", t)
        }
    }
}
