package com.aeonos.autoanswer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log

/**
 * Watches for an incoming call and answers it.
 *
 * Built for a Portal used by someone who can't reliably answer a call themselves: family ring,
 * it picks up, and the Portal's own call UI takes over from there — including its hang-up button,
 * so the person can always end the call. This app deliberately draws nothing over that.
 *
 * Detecting the ring: a ringing call plays a RINGTONE stream, which shows up in
 * AudioManager's playback configurations. That works without being the default dialer and
 * without reading notifications, and it's the same signal that proved reliable on these devices.
 *
 * Answering, in order of preference:
 *   1. TelecomManager.acceptRingingCall() — clean, no UI poking. Meta's calling stack registers
 *      an InCallService, so its calls appear to go through Android's Telecom framework.
 *   2. The accessibility service, which finds the Answer button and clicks it. Slower and more
 *      fragile across UI changes, but it doesn't depend on Telecom co-operating.
 */
class AutoAnswerService : Service() {

    companion object {
        private const val TAG = "AutoAnswer"
        private const val CHANNEL_ID = "autoanswer"
        private const val NOTIF_ID = 1

        /** Set while we're answering, so the accessibility service knows to look for the button. */
        @Volatile var answering = false
            private set

        /** The accessibility service reports back here once it has clicked Answer. */
        fun markAnswered() { answering = false }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AutoAnswerService::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var prefs: Prefs

    @Volatile private var ringing = false
    @Volatile private var lastAnswerAtMs = 0L

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            checkRinging()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        audio = getSystemService(AudioManager::class.java)
        startForeground(NOTIF_ID, buildNotification())
        audio.registerAudioPlaybackCallback(playbackCallback, main)
        Log.i(TAG, "watching for incoming calls (enabled=${prefs.enabled})")
        checkRinging()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restarted by the system, or nudged after a settings change.
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { audio.unregisterAudioPlaybackCallback(playbackCallback) }
        super.onDestroy()
    }

    // ── Ring detection ──────────────────────────────────────────────────────────

    private fun checkRinging() {
        val now = isRingtonePlaying()
        if (now == ringing) return
        ringing = now
        if (!now) { Log.i(TAG, "ringing stopped"); return }

        if (!prefs.enabled) { Log.i(TAG, "ringing — auto-answer is OFF, leaving it"); return }
        // Guard against answering twice for one call if the ringtone stutters.
        val since = System.currentTimeMillis() - lastAnswerAtMs
        if (since < 5_000) { Log.i(TAG, "ringing — already answered ${since}ms ago, ignoring"); return }

        Log.i(TAG, "ringing — answering in ${prefs.delaySeconds}s")
        main.postDelayed({ answer() }, prefs.delaySeconds * 1000L)
    }

    /** A ringtone from another app — i.e. an incoming call, not a sound of ours. */
    private fun isRingtonePlaying(): Boolean {
        val configs = runCatching { audio.activePlaybackConfigurations }.getOrDefault(emptyList())
        return configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_NOTIFICATION_RINGTONE }
    }

    // ── Answering ───────────────────────────────────────────────────────────────

    private fun answer() {
        if (!ringing) { Log.i(TAG, "stopped ringing before we answered"); return }
        lastAnswerAtMs = System.currentTimeMillis()

        if (acceptViaTelecom()) {
            Log.i(TAG, "answered via Telecom")
            return
        }
        // Fall back to the accessibility service, which watches for the Answer button. The flag
        // is cleared on a timer so a failed attempt can't leave it clicking things later.
        Log.i(TAG, "Telecom didn't take it — falling back to tapping Answer")
        answering = true
        main.postDelayed({ answering = false }, 12_000)
    }

    private fun acceptViaTelecom(): Boolean = runCatching {
        val tm = getSystemService(TelecomManager::class.java) ?: return false
        // Needs ANSWER_PHONE_CALLS, granted over adb at setup. Throws SecurityException without it.
        @Suppress("MissingPermission")
        tm.acceptRingingCall()
        true
    }.onFailure { Log.i(TAG, "Telecom accept failed: ${it.message}") }.getOrDefault(false)

    // ── Foreground notification ─────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Auto answer", NotificationManager.IMPORTANCE_MIN))
        }
        val state = if (Prefs(this).enabled) "Calls will be answered automatically"
                    else "Auto answer is off"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Answer")
            .setContentText(state)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .build()
    }
}
