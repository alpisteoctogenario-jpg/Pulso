package app.pulso

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Servicio en primer plano: mantiene viva la sesión con la pantalla apagada o con otra app delante. */
class EngineService : Service() {
    private var wl: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("p", "Sesión", NotificationManager.IMPORTANCE_LOW)
            .apply { lockscreenVisibility = Notification.VISIBILITY_SECRET })
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulso:w").apply { acquire(4 * 60 * 60 * 1000L) }
        val n = build(Engine.ui.value.maxLvl)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, n)
        scope.launch {
            Engine.ui.map { it.maxLvl to it.elapsed / 5 }.distinctUntilChanged()
                .collect { nm.notify(1, build(it.first)) }
        }
    }

    private fun pi(a: String) = PendingIntent.getService(this, a.hashCode(),
        Intent(this, EngineService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(max: Int): Notification = NotificationCompat.Builder(this, "p")
        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Pulso").setContentText("Activo · máx $max")
        .setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "−", pi("down")).addAction(0, "+", pi("up"))
        .addAction(0, "❤ Me gusta", pi("love")).addAction(0, "■ Parar", pi("stop")).build()

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "up" -> Engine.adjust(1); "down" -> Engine.adjust(-1)
            "love" -> Engine.rate(5); "stop" -> Engine.stop()
        }
        return START_NOT_STICKY
    }

    // Seguridad: si deslizas la app fuera de recientes, se para todo.
    override fun onTaskRemoved(rootIntent: Intent?) { Engine.stop(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { scope.cancel(); wl?.let { if (it.isHeld) it.release() }; super.onDestroy() }
}
