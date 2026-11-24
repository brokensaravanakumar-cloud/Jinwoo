package com.jinwoo.ai.wake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.MainActivity
import com.jinwoo.ai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Microphone foreground service that runs the LOCAL keyword spotter. Audio never leaves the device here.
 * Must be started from a user action in the visible app (see WakeWordScreen) - never from boot or the background.
 */
class WakeWordService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    @Volatile private var paused = false
    private val container get() = (application as JinWooApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LISTEN, "Wake word listening", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Wake word detected", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            container.settings.update { it.copy(wakeWordEnabled = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, listeningNotification(),
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        if (loop == null) startLoop()
        return START_NOT_STICKY
    }

    private fun listeningNotification(): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CH_LISTEN)
            .setSmallIcon(R.drawable.ic_stat_jin)
            .setContentTitle("Jin Woo is listening for the wake word")
            .setContentText("Processed on this device only. Tap Stop to turn it off.")
            .setOngoing(true).setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        return try {
            val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400))
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); null } else { r.startRecording(); r }
        } catch (ignored: Exception) { null }
    }

    private fun startLoop() {
        val c = container
        loop = scope.launch {
            val engine = WakeWord.createEngine(this@WakeWordService, c.settings.current.wakeSensitivity)
            if (engine == null) {
                c.wakeBus.state.value = WakeState.Error("Wake-word engine or model files are missing. Open Wake Word setup.")
                stopSelf(); return@launch
            }
            c.wakeBus.state.value = WakeState.Listening
            val ctl = launch {
                c.wakeBus.control.collect {
                    paused = it == WakeControl.Pause
                    c.wakeBus.state.value = if (paused) WakeState.Paused else WakeState.Listening
                }
            }
            var rec: AudioRecord? = null
            var error: String? = null
            var cooldownUntil = 0L
            val buf = ShortArray(1600)
            try {
                while (isActive) {
                    if (paused) { rec?.let { runCatching { it.stop(); it.release() } }; rec = null; delay(100); continue }
                    if (rec == null) {
                        rec = openRecorder()
                        if (rec == null) { error = "Could not open the microphone (permission denied or in use)."; break }
                    }
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) { error = "Microphone read error ($n)."; break }
                    if (n == 0 || System.currentTimeMillis() < cooldownUntil) continue
                    if (engine.process(buf, n)) {
                        cooldownUntil = System.currentTimeMillis() + 2500
                        onWakeDetected()
                    }
                }
            } finally {
                rec?.let { runCatching { it.stop(); it.release() } }
                runCatching { engine.close() }
                ctl.cancel()
            }
            c.wakeBus.state.value = if (error != null) WakeState.Error(error) else WakeState.Stopped
            stopSelf()
        }
    }

    private fun onWakeDetected() {
        val c = container
        if (c.appInForeground) {
            c.wakeBus.detected.tryEmit(Unit)
        } else if (c.settings.current.wakeAlertNotification) {
            // Android restricts launching activities from the background: use a notification the user can tap.
            val pi = PendingIntent.getActivity(this, 3,
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_AUTO_LISTEN, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_jin).setContentTitle("JIN WOO heard you")
                .setContentText("Tap to open the System and give your command.")
                .setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL).build()
            getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        val bus = container.wakeBus
        if (bus.state.value !is WakeState.Error) bus.state.value = WakeState.Stopped
        super.onDestroy()
    }

    companion object {
        private const val CH_LISTEN = "wake_listen"
        private const val CH_ALERT = "wake_alert"
        private const val NOTIF_ID = 4101
        private const val ALERT_ID = 4102
        const val ACTION_STOP = "com.jinwoo.ai.STOP_WAKE"

        /** Call only from a visible, user-initiated flow (e.g. toggling the switch in the app). */
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, WakeWordService::class.java)) }
    }
}
