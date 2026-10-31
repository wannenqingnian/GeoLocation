package io.github.ceigt.geolocation.manager.route

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.manager.MainActivity
import io.github.ceigt.geolocation.manager.mock.MockLocationService
import io.github.ceigt.geolocation.manager.root.RootLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RouteSimulationService : Service() {

    companion object {
        private const val TAG = "RouteSimulationService"
        private const val CHANNEL_ID = "route_simulation_channel"
        private const val NOTIFICATION_ID = 3001

        const val ACTION_PAUSE = "io.github.ceigt.geolocation.action.ROUTE_PAUSE"
        const val ACTION_RESUME = "io.github.ceigt.geolocation.action.ROUTE_RESUME"
        const val ACTION_STOP = "io.github.ceigt.geolocation.action.ROUTE_STOP"

        val engine = RouteSimulationEngine()

        fun startService(context: Context) {
            val intent = Intent(context, RouteSimulationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RouteSimulationService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var repository: PreferencesRepository
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile private var isRunning = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val result = engine.tick()
            if (result != null) {
                scope.launch {
                    repository.saveLastClickedLocation(result.latitude, result.longitude)
                    if (result.speedMps > 0) {
                        repository.saveSpeed(result.speedMps)
                        repository.saveUseSpeed(true)
                    }
                }
                val notification = buildNotification(
                    "路线当前位点: ${"%.5f".format(result.latitude)}, ${"%.5f".format(result.longitude)} | ${"%.1f".format(result.speedMps)} m/s"
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, notification)
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = PreferencesRepository(this)
        createNotificationChannel()

        val notification = buildNotification("路线模拟正在运行...")
        startForeground(NOTIFICATION_ID, notification)

        thread = HandlerThread("RouteSimulationWorker").apply { start() }
        handler = Handler(thread.looper)
        isRunning = true
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            ACTION_STOP -> {
                engine.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        engine.stop()
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_stat_location)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "路线模拟服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

class RoutePictureInPictureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RouteSimulationService.ACTION_PAUSE -> RouteSimulationService.engine.pause()
            RouteSimulationService.ACTION_RESUME -> RouteSimulationService.engine.resume()
            RouteSimulationService.ACTION_STOP -> RouteSimulationService.stopService(context)
        }
    }
}
