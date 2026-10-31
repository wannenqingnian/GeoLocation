package io.github.ceigt.geolocation.manager.root

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.DEFAULT_ACCURACY
import io.github.ceigt.geolocation.data.KEY_ACCURACY
import io.github.ceigt.geolocation.data.KEY_ENABLE_ROOT_MODE
import io.github.ceigt.geolocation.data.KEY_IS_PLAYING
import io.github.ceigt.geolocation.data.KEY_LAST_CLICKED_LOCATION
import io.github.ceigt.geolocation.data.KEY_USE_ACCURACY
import io.github.ceigt.geolocation.data.SHARED_PREFS_FILE
import io.github.ceigt.geolocation.inject.RootEntry
import io.github.ceigt.geolocation.inject.RootLocationHook
import io.github.ceigt.geolocation.manager.mock.MockFixFactory

class RootLocationService : Service() {

    companion object {
        private const val TAG = "RootLocationService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "root_location_channel"
        private const val TICK_INTERVAL_MS = 1000L

        fun sync(context: Context, shouldRun: Boolean) {
            val intent = Intent(context, RootLocationService::class.java)
            if (shouldRun) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    private lateinit var handler: Handler
    private lateinit var thread: HandlerThread
    private lateinit var powerManager: PowerManager
    private lateinit var locationManager: LocationManager
    private lateinit var preferences: SharedPreferences
    @Volatile private var isRunning = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                pushRootLocation()
            } catch (t: Throwable) {
                Log.e(TAG, "Error pushing location via root: ${t.message}", t)
            }
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        preferences = getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Root 独立注入模式运行中 (已配置系统定位源)")
            .setSmallIcon(R.drawable.ic_stat_location)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        thread = HandlerThread("RootLocationServiceWorker").apply { start() }
        handler = Handler(thread.looper)

        RootEntry.bypassHiddenApiRestrictions()
        // Ensure appops permission is granted via Root
        RootLocationHook.ensureRootEnvironment(this)

        isRunning = true
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playing = preferences.getBoolean(KEY_IS_PLAYING, false)
        val rootMode = preferences.getBoolean(KEY_ENABLE_ROOT_MODE, false)
        if (!playing || !rootMode) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        // Clean up providers
        RootLocationHook.removeProviders(locationManager)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pushRootLocation() {
        val pointJson = preferences.getString(KEY_LAST_CLICKED_LOCATION, null) ?: return
        val point = runCatching { io.github.ceigt.geolocation.data.JsonCodec.decodeLocation(pointJson) }.getOrNull() ?: return
        val accuracy = if (preferences.getBoolean(KEY_USE_ACCURACY, false)) {
            val bits = preferences.getLong(KEY_ACCURACY, java.lang.Double.doubleToRawLongBits(DEFAULT_ACCURACY))
            java.lang.Double.longBitsToDouble(bits).toFloat()
        } else {
            5f
        }

        val allPrefs = preferences.all
        val fix = MockFixFactory.create(point, accuracy, allPrefs)

        // Inject into GPS, Network, and Fused system providers
        for (provider in RootLocationHook.TARGET_PROVIDERS) {
            RootLocationHook.injectLocation(locationManager, provider, fix)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Root Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
