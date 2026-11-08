package com.socialsaver

import android.app.Activity
import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.widget.VideoView
import java.io.File

class PreviewActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        getSystemService(NotificationManager::class.java).cancel(2)
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        val video = intent.getBooleanExtra("video", false)
        val f = File(path)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)) }
        root.addView(txt(if (video) "Video preview" else "Image preview", 18f, true))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420))
        if (video) {
            val vv = VideoView(this)
            root.addView(vv, lp)
            vv.setVideoPath(path)
            vv.setOnPreparedListener { it.isLooping = true; it.start() }
        } else {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageURI(Uri.fromFile(f)) }
            root.addView(iv, lp)
        }
        root.addView(row(
            btn("Discard") { f.delete(); finish() },
            btn("Save") { save(f, video) }
        ))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onStart() { super.onStart(); Bus.setDialog(true) }
    override fun onStop() { super.onStop(); Bus.setDialog(false) }

    private fun save(f: File, video: Boolean) {
        Thread {
            try {
                val ext = if (video) "mp4" else "jpg"
                SaveUtil.copyFile(this, f, "capture_${SaveUtil.stamp()}.$ext", if (video) "video/mp4" else "image/jpeg", video)
                f.delete()
                runOnUiThread {
                    Toast.makeText(this, "Saved to " + (if (video) "Movies" else "Pictures") + "/SocialSaver", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
