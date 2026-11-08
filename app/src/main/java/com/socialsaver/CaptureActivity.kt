package com.socialsaver

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView

class CaptureActivity : Activity() {
    private lateinit var mode: String
    private lateinit var quality: RadioGroup
    private var length: RadioGroup? = null
    private var q = 100
    private var secs = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        mode = intent.getStringExtra("mode") ?: "photo"
        val photo = mode == "photo"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)) }
        root.addView(txt(if (photo) "Capture photo" else "Record video", 18f, true))
        root.addView(txt("Quality", 16f, true))
        quality = radioGroup(
            if (photo) listOf("Full size" to 100, "Medium (75%)" to 75, "Small (50%)" to 50)
            else listOf("1080p (best)" to 1080, "720p" to 720, "480p (small file)" to 480),
            if (photo) 0 else 1
        )
        root.addView(quality)
        if (!photo) {
            root.addView(txt("Recording length", 16f, true))
            length = radioGroup(listOf("15 seconds" to 15, "30 seconds" to 30, "60 seconds" to 60, "2 minutes" to 120), 1)
            root.addView(length)
        }
        root.addView(txt(
            if (photo) "Android will ask permission to capture the screen. The photo is taken about a second after you allow it. You can preview before saving."
            else "Android will ask permission to record the screen. Recording starts about a second later and stops by itself (or use Stop in the notification). Video is recorded without sound. You can preview before saving."
        ))
        root.addView(row(btn("Cancel") { finish() }, btn("Start") { start() }))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onStart() { super.onStart(); Bus.setDialog(true) }
    override fun onStop() { super.onStop(); Bus.setDialog(false) }

    private fun start() {
        q = quality.value()
        secs = length?.value() ?: 0
        Bus.setCapturing(true)   // hide the glass button so it isn't in the capture
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), 42)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 42) {
            if (res == RESULT_OK && data != null) {
                startForegroundService(
                    Intent(this, CaptureService::class.java)
                        .putExtra("mode", mode).putExtra("quality", q).putExtra("seconds", secs)
                        .putExtra("code", res).putExtra("data", data)
                )
            } else Bus.setCapturing(false)
            finish()
        }
    }
}
