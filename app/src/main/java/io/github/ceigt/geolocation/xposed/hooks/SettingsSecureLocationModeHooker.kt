package io.github.ceigt.geolocation.xposed.hooks

import android.content.ContentResolver
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

/**
 * Spoofs system location master switches and provider enabled states in target applications:
 * 1. LocationManager.isLocationEnabled() -> true
 * 2. LocationManager.isProviderEnabled("gps" | "network" | "fused") -> true
 * 3. Settings.Secure.getInt(..., "location_mode", ...) -> 3 (LOCATION_MODE_HIGH_ACCURACY)
 * 4. Settings.Secure.getIntForUser(..., "location_mode", ...) -> 3
 */
class SettingsSecureLocationModeHooker(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationModeHooker]"
    private val installedMethods = mutableSetOf<Method>()

    fun initHooks(loader: ClassLoader = classLoader) {
        isolateHook({ module.log(Log.WARN, tag, "Location mode hooks failed: ${it.message}") }) {
            hookLocationManagerSwitches(loader)
            hookSettingsSecureLocationMode(loader)
            module.log(Log.INFO, tag, "Location mode & master switch spoofing hooks active")
        }
    }

    private fun hookLocationManagerSwitches(loader: ClassLoader) {
        val locationManagerClass = LocationManager::class.java

        // isLocationEnabled() (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val method = locationManagerClass.getMethod("isLocationEnabled")
                hook(method) { chain ->
                    if (PreferencesUtil.getIsPlaying()) {
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        // isProviderEnabled(String)
        runCatching {
            val method = locationManagerClass.getMethod("isProviderEnabled", String::class.java)
            hook(method) { chain ->
                val provider = chain.getArg(0) as? String
                if (PreferencesUtil.getIsPlaying() && (
                            provider == LocationManager.GPS_PROVIDER ||
                            provider == LocationManager.NETWORK_PROVIDER ||
                            provider == "fused"
                        )
                ) {
                    true
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private fun hookSettingsSecureLocationMode(loader: ClassLoader) {
        val secureClass = Settings.Secure::class.java

        // Settings.Secure.getInt(ContentResolver, String, int)
        runCatching {
            val method = secureClass.getMethod(
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hook(method) { chain ->
                val name = chain.getArg(1) as? String
                if (PreferencesUtil.getIsPlaying() && name == Settings.Secure.LOCATION_MODE) {
                    // LOCATION_MODE_HIGH_ACCURACY = 3
                    3
                } else {
                    chain.proceed()
                }
            }
        }

        // Settings.Secure.getInt(ContentResolver, String)
        runCatching {
            val method = secureClass.getMethod(
                "getInt",
                ContentResolver::class.java,
                String::class.java
            )
            hook(method) { chain ->
                val name = chain.getArg(1) as? String
                if (PreferencesUtil.getIsPlaying() && name == Settings.Secure.LOCATION_MODE) {
                    3
                } else {
                    chain.proceed()
                }
            }
        }

        // Settings.Secure.getIntForUser(ContentResolver, String, int, int)
        runCatching {
            val method = secureClass.getMethod(
                "getIntForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            hook(method) { chain ->
                val name = chain.getArg(1) as? String
                if (PreferencesUtil.getIsPlaying() && name == Settings.Secure.LOCATION_MODE) {
                    3
                } else {
                    chain.proceed()
                }
            }
        }

        // Settings.Secure.getString(ContentResolver, String) -> LOCATION_PROVIDERS_ALLOWED
        runCatching {
            val method = secureClass.getMethod(
                "getString",
                ContentResolver::class.java,
                String::class.java
            )
            hook(method) { chain ->
                val name = chain.getArg(1) as? String
                if (PreferencesUtil.getIsPlaying() && name == Settings.Secure.LOCATION_PROVIDERS_ALLOWED) {
                    "gps,network"
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private fun hook(method: Method, interceptor: Hooker) {
        synchronized(installedMethods) {
            if (method in installedMethods) return
            runCatching {
                module.hook(method).intercept(interceptor)
                installedMethods.add(method)
            }
        }
    }
}
