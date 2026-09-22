package com.aeonos.autoanswer

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("autoanswer", Context.MODE_PRIVATE)

    /** Master switch. Deliberately OFF on a fresh install — nothing answers until it's chosen. */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /**
     * How long to let it ring first. A couple of seconds means the person has a moment to notice
     * the call and answer it themselves, and it isn't jarring for the caller either.
     */
    var delaySeconds: Int
        get() = sp.getInt("delay_seconds", 3).coerceIn(0, 30)
        set(v) = sp.edit().putInt("delay_seconds", v.coerceIn(0, 30)).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean("start_on_boot", true)
        set(v) = sp.edit().putBoolean("start_on_boot", v).apply()
}
