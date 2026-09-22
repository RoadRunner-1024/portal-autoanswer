package com.aeonos.autoanswer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Lets the provisioning script configure the app over adb, so a Portal can be set up end to end
 * from a computer without anyone tapping through settings on the device itself. That matters
 * here: these are typically being set up for someone else, often remotely.
 *
 *   adb shell am broadcast -a com.aeonos.autoanswer.CONFIG --ez enabled true --ei delay 3
 */
class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        intent ?: return
        val prefs = Prefs(context)
        var touched = false

        if (intent.hasExtra("enabled")) {
            prefs.enabled = intent.getBooleanExtra("enabled", false); touched = true
        }
        if (intent.hasExtra("delay")) {
            prefs.delaySeconds = intent.getIntExtra("delay", 3); touched = true
        }
        if (intent.hasExtra("boot")) {
            prefs.startOnBoot = intent.getBooleanExtra("boot", true); touched = true
        }
        if (!touched) return

        Log.i("AutoAnswer", "config set over adb: enabled=${prefs.enabled} " +
            "delay=${prefs.delaySeconds}s boot=${prefs.startOnBoot}")
        // Restart the service so the change (and its notification text) takes effect at once.
        AutoAnswerService.start(context)
    }
}
