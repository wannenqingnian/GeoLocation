package io.github.ceigt.geolocation.xposed

import android.app.Application
import android.util.Log
import android.widget.Toast
import io.github.ceigt.geolocation.data.REMOTE_PREFS_GROUP
import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME
import io.github.ceigt.geolocation.data.SystemHookSupport
import io.github.ceigt.geolocation.data.model.mode.HookInstallPolicy
import android.os.Build
import io.github.ceigt.geolocation.xposed.hooks.isolateHook
import io.github.ceigt.geolocation.xposed.hooks.LocationApiHooks
import io.github.ceigt.geolocation.xposed.hooks.PhoneServicesHooks
import io.github.ceigt.geolocation.xposed.hooks.SystemServicesHooks
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.Collections
import java.util.WeakHashMap

class ModuleEntry : XposedModule() {
    companion object {
        const val TAG = "[ModuleEntry]"
        private const val PHONE_PACKAGE = "com.android.phone"
    }

    private var locationApiHooks: LocationApiHooks? = null
    private var systemServicesHooks: SystemServicesHooks? = null
    private var isSystemServer = false

    // Record hooked ClassLoaders to prevent duplicate attachment under dynamic class loading
    private val hookedClassLoaders: MutableSet<ClassLoader> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
        LocationUtil.logger = { priority, tag, message -> log(priority, tag, message) }
        PreferencesUtil.logger = { priority, tag, message -> log(priority, tag, message) }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        log(Log.INFO, TAG, "onPackageLoaded: ${param.packageName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "onPackageReady: ${param.packageName}")

        if (isSystemServer) {
            systemServicesHooks?.onPackageReady(param.packageName, param.classLoader)
            return
        }

        // Run per-package setup only once.
        if (!param.isFirstPackage) return

        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))
        LocationUtil.targetPackageName = param.packageName

        if (param.packageName == PHONE_PACKAGE) {
            // System-hook mode must also cover tower-derived positioning. App-level hooks do not
            // belong in the phone process, so install only the dedicated telephony identity guard.
            isolateHook({ log(Log.WARN, TAG, "Phone Services adapters unavailable: ${it.javaClass.simpleName}") }) {
                PhoneServicesHooks(this, param.classLoader).initHooks()
            }
        } else if (param.packageName == MANAGER_APP_PACKAGE_NAME) {
            // The manager must always read the device's real location for the “My location” map
            // control. This also protects users who accidentally add the manager to Xposed scope.
            log(Log.INFO, TAG, "Skipping location hooks for the manager app.")
        } else {
            initHookingLogic(param)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        isSystemServer = true
        log(Log.INFO, TAG, "onSystemServerStarting")

        if (!SystemHookSupport.supports(Build.VERSION.SDK_INT)) {
            log(Log.WARN, TAG, "System hooks disabled on unverified SDK ${Build.VERSION.SDK_INT}")
            return
        }

        // system_server is a hooked process only when the user enabled system-level hooks (which adds
        // "system"/"android" to the module scope). Each intercept checks the current playing state
        // and system-mode setting before replacing supported system location results.
        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))
        LocationUtil.targetPackageName = "system"
        systemServicesHooks = SystemServicesHooks(this, param.classLoader).also { it.initHooks() }
    }

    private fun initHookingLogic(param: PackageReadyParam) {
        val policy = PreferencesUtil.getAppHookInstallPolicy(param.packageName)
        log(Log.INFO, TAG, "Initializing hooks for ${param.packageName} with policy $policy")

        when (policy) {
            HookInstallPolicy.IMMEDIATE -> {
                installAppHooks(param.classLoader, param.packageName)
                installApplicationReadyHook(param.classLoader, param.packageName)
            }
            HookInstallPolicy.DYNAMIC_CLASSLOAD -> {
                installAppHooks(param.classLoader, param.packageName)
                installDynamicClassLoaderHook(param.classLoader, param.packageName)
                installApplicationReadyHook(param.classLoader, param.packageName)
            }
            HookInstallPolicy.APPLICATION_CREATE -> {
                installApplicationReadyHook(param.classLoader, param.packageName, attachAppHooksOnCreate = true)
            }
        }
    }

    private fun installAppHooks(loader: ClassLoader, packageName: String) {
        if (!hookedClassLoaders.add(loader)) return
        isolateHook({ log(Log.WARN, TAG, "Application adapters unavailable for loader $loader: ${it.javaClass.simpleName}") }) {
            LocationApiHooks(this, loader).also {
                locationApiHooks = it
                it.initHooks()
                log(Log.INFO, TAG, "Installed LocationApiHooks on classLoader: $loader for $packageName")
            }
        }
    }

    private fun installDynamicClassLoaderHook(baseLoader: ClassLoader, packageName: String) {
        isolateHook({ log(Log.WARN, TAG, "Dynamic ClassLoader hook failed: ${it.javaClass.simpleName}") }) {
            val loadClassMethod = ClassLoader::class.java.getDeclaredMethod(
                "loadClass",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )

            hook(loadClassMethod).intercept { chain ->
                val result = chain.proceed()
                val targetClassLoader = (chain.thisObject as? ClassLoader)
                if (targetClassLoader != null && !hookedClassLoaders.contains(targetClassLoader)) {
                    val className = chain.getArg(0) as? String
                    // When shell unpacker loads any application or SDK classes, dynamically attach
                    if (className != null && (
                            className.startsWith(packageName) ||
                            className.contains("location", ignoreCase = true) ||
                            className.contains("amap", ignoreCase = true) ||
                            className.contains("baidu", ignoreCase = true) ||
                            className.contains("tencent", ignoreCase = true)
                        )
                    ) {
                        installAppHooks(targetClassLoader, packageName)
                    }
                }
                result
            }
            log(Log.INFO, TAG, "Installed dynamic ClassLoader loadClass hook for $packageName")
        }
    }

    private fun installApplicationReadyHook(
        classLoader: ClassLoader,
        packageName: String,
        attachAppHooksOnCreate: Boolean = false
    ) {
        isolateHook({ log(Log.WARN, TAG, "Application-ready retry unavailable: ${it.javaClass.simpleName}") }) {
            val method = Class.forName("android.app.Instrumentation", false, classLoader)
                .getDeclaredMethod("callApplicationOnCreate", Application::class.java)

            hook(method).intercept { chain ->
                val result = chain.proceed()

                try {
                    val app = chain.getArg(0) as Application
                    val context = app.applicationContext

                    if (attachAppHooksOnCreate) {
                        val realLoader = app.classLoader ?: context.classLoader
                        if (realLoader != null) {
                            installAppHooks(realLoader, packageName)
                        }
                    }

                    locationApiHooks?.onApplicationReady(context)
                    log(Log.INFO, TAG, "Target App's context has been acquired ($packageName).")
                    if (PreferencesUtil.getIsPlaying() && PreferencesUtil.getHideFakeLocationToast() != true) {
                        Toast.makeText(context, "Fake Location Is Active!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    log(Log.ERROR, TAG, "Toast/context failed - ${e.message}")
                }
                result
            }
        }
    }
}
