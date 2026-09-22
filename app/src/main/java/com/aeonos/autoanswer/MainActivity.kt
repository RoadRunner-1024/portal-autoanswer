package com.aeonos.autoanswer

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Setup and status. Two things have to be granted for this to work, and neither can be done from
 * inside the app on a Portal, so the screen's job is mostly to say plainly what's missing.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<Switch>(R.id.sw_enabled).setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            AutoAnswerService.start(this)
            updateStatus()
        }
        findViewById<Switch>(R.id.sw_boot).setOnCheckedChangeListener { _, checked ->
            prefs.startOnBoot = checked
        }
        findViewById<SeekBar>(R.id.seek_delay).apply {
            max = 15
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.delaySeconds = p; updateStatus() }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        AutoAnswerService.start(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        findViewById<Switch>(R.id.sw_enabled).isChecked = prefs.enabled
        findViewById<Switch>(R.id.sw_boot).isChecked = prefs.startOnBoot
        findViewById<SeekBar>(R.id.seek_delay).progress = prefs.delaySeconds
        findViewById<TextView>(R.id.tv_delay).text =
            if (prefs.delaySeconds == 0) "Answer immediately"
            else "Ring for ${prefs.delaySeconds} seconds first"

        val canTelecom = checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED
        val accessibilityOn = isAccessibilityEnabled()

        // Either route alone is enough to answer a call; say so rather than demanding both.
        val ready = canTelecom || accessibilityOn
        findViewById<TextView>(R.id.tv_status).text = when {
            !ready -> "Not ready — no way to answer calls yet. Grant one of the two below."
            prefs.enabled -> "Ready. Incoming calls will be answered automatically."
            else -> "Ready, but auto answer is switched off."
        }
        findViewById<TextView>(R.id.tv_telecom).text =
            if (canTelecom) "✓ Answer permission granted (preferred method)"
            else "✗ Answer permission not granted — run this from a computer:\n" +
                 "adb shell pm grant $packageName android.permission.ANSWER_PHONE_CALLS"
        findViewById<TextView>(R.id.tv_accessibility).text =
            if (accessibilityOn) "✓ Accessibility service on (fallback method)"
            else "✗ Accessibility service off — the backup way of pressing Answer"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
