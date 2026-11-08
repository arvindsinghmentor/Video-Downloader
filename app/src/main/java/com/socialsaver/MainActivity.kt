package com.socialsaver

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(40), dp(20), dp(20)) }
        root.addView(txt("Social Saver", 24f, true))
        status = txt("", 15f, true)
        root.addView(status)
        root.addView(txt(
            "Setup (one time):\n" +
                "1. Tap 'Enable in Accessibility' and switch on Social Saver. " +
                "If the switch is greyed out: Settings > Apps > Social Saver > ⋮ menu > 'Allow restricted settings', then try again.\n" +
                "2. Tap 'Turn floating button ON', or add the 'Social Saver' tile to your top drawer (pencil / Edit tiles).\n\n" +
                "The glass button appears only inside Instagram and Facebook. Tap it to open:\n" +
                "• Download icon: copy the post link first (Share > Copy link), then tap it. You get a preview and quality choice.\n" +
                "• Photo icon: captures what is on screen, with preview before saving.\n" +
                "• Video icon: records the screen (no sound), with preview before saving.\n\n" +
                "Drag the button anywhere. Drag it onto the red X to turn it off.\n\n" +
                "Only save content you made or have permission to keep."
        ))
        root.addView(btn("Enable in Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(btn("Turn floating button ON") { Prefs.setOn(this, true); update() })
        root.addView(btn("Turn floating button OFF") { Prefs.setOn(this, false); update() })
        setContentView(ScrollView(this).apply { addView(root) })
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() { super.onResume(); update() }

    private fun update() {
        status.text = "Accessibility: " + (if (HelperService.instance != null) "ON ✔" else "OFF ✗") +
            "\nFloating button: " + (if (Prefs.isOn(this)) "ON" else "OFF")
    }
}
