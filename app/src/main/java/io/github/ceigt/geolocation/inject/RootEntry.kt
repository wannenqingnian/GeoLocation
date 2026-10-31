package io.github.ceigt.geolocation.inject

import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

object RootEntry {
    private const val TAG = "GeoLocationRootEntry"

    fun init() {
        Log.i(TAG, "Initializing RootEntry and bypassing hidden API restrictions...")
        bypassHiddenApiRestrictions()
    }

    fun bypassHiddenApiRestrictions(): Boolean {
        // First try LSPosed HiddenApiBypass
        val bypassed = runCatching {
            HiddenApiBypass.addHiddenApiExemptions("")
        }.isSuccess

        if (bypassed) {
            Log.i(TAG, "Hidden API restrictions bypassed via HiddenApiBypass")
            return true
        }

        // Fallback to meta-reflection on VMRuntime
        return runCatching {
            val forNameMethod = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod("getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java)

            val vmRuntimeClass = forNameMethod.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntimeMethod = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as java.lang.reflect.Method
            val setExemptionsMethod = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf(Array<String>::class.java)
            ) as java.lang.reflect.Method

            val runtime = getRuntimeMethod.invoke(null)
            setExemptionsMethod.invoke(runtime, arrayOf("L"))
            Log.i(TAG, "Hidden API restrictions bypassed via VMRuntime reflection fallback")
            true
        }.onFailure {
            Log.e(TAG, "Failed to bypass hidden API restrictions: ${it.message}", it)
        }.getOrDefault(false)
    }
}
