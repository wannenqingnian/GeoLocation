package io.github.ceigt.geolocation.manager.root

import android.content.Context
import android.provider.Settings
import android.util.Log

object RootEnvironmentAdapter {
    private const val TAG = "RootEnvironmentAdapter"
    private const val STATE_PREFS = "root_environment_state"
    private const val KEY_WAS_SUPPRESSED = "was_suppressed"
    private const val KEY_ORIG_WIFI_SCAN = "orig_wifi_scan"
    private const val KEY_ORIG_BLE_SCAN = "orig_ble_scan"

    fun suppressRealLocationEnvironment(context: Context) {
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

        // Read and save original user setting values before changing them
        val origWifi = runCatching {
            Settings.Global.getInt(context.contentResolver, "wifi_scan_always_enabled", 0)
        }.getOrDefault(0)

        val origBle = runCatching {
            Settings.Global.getInt(context.contentResolver, "ble_scan_always_enabled", 0)
        }.getOrDefault(0)

        prefs.edit()
            .putInt(KEY_ORIG_WIFI_SCAN, origWifi)
            .putInt(KEY_ORIG_BLE_SCAN, origBle)
            .putBoolean(KEY_WAS_SUPPRESSED, true)
            .apply()

        Log.i(TAG, "Suppressing real location scanning (origWifi=$origWifi, origBle=$origBle)...")

        val commands = listOf(
            "settings put global wifi_scan_always_enabled 0",
            "settings put global ble_scan_always_enabled 0",
            "pm disable-user --user 0 com.xiaomi.metoknlp 2>/dev/null",
            "am stopservice -n com.xiaomi.metoknlp/com.xiaomi.location.metoknlp.MetokLocationService 2>/dev/null"
        )

        for (cmd in commands) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
            }
        }
    }

    fun restoreRealLocationEnvironment(context: Context) {
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_WAS_SUPPRESSED, false)) return

        // Read saved original values instead of forcing to 1
        val origWifi = prefs.getInt(KEY_ORIG_WIFI_SCAN, 0)
        val origBle = prefs.getInt(KEY_ORIG_BLE_SCAN, 0)

        Log.i(TAG, "Restoring real location scanning to user originals (origWifi=$origWifi, origBle=$origBle)...")
        val commands = listOf(
            "settings put global wifi_scan_always_enabled $origWifi",
            "settings put global ble_scan_always_enabled $origBle",
            "pm enable com.xiaomi.metoknlp 2>/dev/null",
            "am startservice -n com.xiaomi.metoknlp/com.xiaomi.location.metoknlp.MetokLocationService 2>/dev/null"
        )

        for (cmd in commands) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
            }
        }
        prefs.edit().putBoolean(KEY_WAS_SUPPRESSED, false).apply()
    }
}
