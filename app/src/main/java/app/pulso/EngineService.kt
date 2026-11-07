package app.pulso

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/** Servicio en primer plano: mantiene la sesión, la burbuja y las notificaciones. */
class EngineService : Service() {
    private var wl: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var bubble: Bubble

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        bubble = Bubble(this)
        bubble.onError = { Engine.toast("Burbuja: $it") }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("p", "Sesión", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET })
        nm.createNotificationChannel(NotificationChannel("a", "Preguntas", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET })
        nm.createNotificationChannel(NotificationChannel("m", "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_SECRET })
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulso:w").apply { acquire(4 * 60 * 60 * 1000L) }
        val n = build(Engine.ui.value)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(1, n)
        scope.launch {
            var lastKey = ""; var lastAsk = false; var lastMsg = ""
            Engine.ui.collect { s ->
                if (s.running && s.bubble) { bubble.show(); bubble.update(s) } else bubble.hide()
                val key = "${(s.gain * 100).toInt()}|${s.elapsed / 5}|${s.joiText}"
                if (key != lastKey) { lastKey = key; nm.notify(1, build(s)) }
                if (s.ask != lastAsk) {
                    lastAsk = s.ask
                    if (s.ask && !Settings.canDrawOverlays(this@EngineService)) nm.notify(2, askNotif()) else nm.cancel(2)
                }
                if (s.joiText != lastMsg) {
                    lastMsg = s.joiText
                    // Sin permiso de burbuja: los mensajes salen como notificación emergente sobre otras apps
                    if (s.running && s.joiText.isNotEmpty() && !Settings.canDrawOverlays(this@EngineService)) nm.notify(3, msgNotif(s.joiText))
                }
            }
        }
    }

    private fun pi(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, EngineService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(s: Ui): Notification = NotificationCompat.Builder(this, "p")
        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Pulso")
        .setContentText(if (s.joiText.isNotEmpty()) s.joiText else "Activo · caña ${(s.gain * 100).toInt()}%")
        .setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "− caña", pi("down")).addAction(0, "+ caña", pi("up")).addAction(0, "❤", pi("love")).addAction(0, "■ Parar", pi("stop")).build()

    private fun msgNotif(t: String): Notification = NotificationCompat.Builder(this, "m")
        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("💬").setContentText(t)
        .setStyle(NotificationCompat.BigTextStyle().bigText(t)).setPriority(NotificationCompat.PRIORITY_HIGH)
        .setTimeoutAfter(9000).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .addAction(0, "− caña", pi("down")).addAction(0, "+ caña", pi("up")).addAction(0, "❤", pi("love")).build()

    private fun askNotif(): Notification = NotificationCompat.Builder(this, "a")
        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Pulso").setContentText("¿Cómo vas?")
        .setAutoCancel(true).setTimeoutAfter(25000).setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .addAction(0, "Más", pi("ans_more")).addAction(0, "Igual", pi("ans_same")).addAction(0, "Menos", pi("ans_less")).build()

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "up" -> Engine.adjust(1); "down" -> Engine.adjust(-1)
            "love" -> Engine.rate(5); "stop" -> Engine.stop()
            "ans_more" -> Engine.answer("more"); "ans_same" -> Engine.answer("same"); "ans_less" -> Engine.answer("less")
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) { Engine.stop(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { scope.cancel(); bubble.hide(); wl?.let { if (it.isHeld) it.release() }; super.onDestroy() }
}
