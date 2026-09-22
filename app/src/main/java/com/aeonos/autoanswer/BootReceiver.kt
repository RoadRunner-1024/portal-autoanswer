package com.aeonos.autoanswer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Start watching again after a reboot — the Portal is expected to look after itself. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs(context).startOnBoot) AutoAnswerService.start(context)
    }
}
