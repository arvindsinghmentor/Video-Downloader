package com.socialsaver

import android.app.Activity
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.VideoView

class LinkActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var group: RadioGroup
    private var video: VideoView? = null
    private var started = false
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)) }
        setContentView(ScrollView(this).apply { addView(root) })
        busy("Checking clipboard…")
    }

    override fun onStart() { super.onStart(); Bus.setDialog(true) }
    override fun onStop() { super.onStop(); Bus.setDialog(false) }

    // Clipboard can only be read while this window has focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !started) { started = true; readClipboard() }
    }

    private fun reset() { root.removeAllViews() }

    private fun message(title: String, body: String) {
        reset()
        root.addView(txt(title, 18f, true))
        root.addView(txt(body))
        root.addView(btn("Close") { finish() })
    }

    private fun busy(text: String) {
        reset()
        root.addView(txt(text, 16f, true))
        root.addView(ProgressBar(this))
    }

    private fun readClipboard() {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        val hosts = listOf("instagram.com", "instagr.am", "facebook.com", "fb.watch", "fb.com")
        val link = Regex("https?://\\S+").findAll(text).map { it.value }.firstOrNull { u -> hosts.any { u.contains(it) } }
        if (link == null) {
            message(
                "No link found",
                "Open the post, reel or story, tap Share > Copy link, then tap the glass button again and choose the download icon."
            )
            return
        }
        busy("Reading the post…")
        Thread {
            try {
                val r = Extractor.fetch(link)
                runOnUiThread { showOptions(r) }
            } catch (e: Exception) {
                runOnUiThread {
                    message("Couldn't read this post", (e.message ?: "") + "\n\nTry the Photo or Video screen-capture icon instead.")
                }
            }
        }.start()
    }

    private fun showOptions(r: Extractor.Result) {
        reset()
        root.addView(txt(if (r.video) "Video preview" else "Image preview", 18f, true))
        if (r.video) {
            val vv = VideoView(this); video = vv
            root.addView(vv, LinearLayout.LayoutParams(MATCH, dp(320)))
            play(r.options[0].url)
        } else {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            root.addView(iv, LinearLayout.LayoutParams(MATCH, dp(320)))
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(Extractor.open(r.options[0].url).inputStream)
                    runOnUiThread { iv.setImageBitmap(bmp) }
                } catch (_: Exception) {}
            }.start()
        }
        root.addView(txt("Choose quality", 16f, true))
        group = radioGroup(r.options.mapIndexed { i, o -> o.label to i }) {
            if (r.video) play(r.options[group.value()].url)
        }
        root.addView(group)
        root.addView(row(
            btn("Cancel") { finish() },
            btn("Download") { download(r.options[group.value()], r.video) }
        ))
    }

    private fun play(url: String) {
        val vv = video ?: return
        vv.setVideoURI(Uri.parse(url), mapOf("User-Agent" to "Mozilla/5.0"))
        vv.setOnPreparedListener { it.isLooping = true; it.start() }
    }

    private fun download(opt: Extractor.Option, isVideo: Boolean) {
        video?.stopPlayback()
        reset()
        val info = txt("Downloading…", 16f, true)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(info); root.addView(bar)
        Thread {
            try {
                val conn = Extractor.open(opt.url)
                val total = conn.contentLength
                val ext = if (isVideo) "mp4" else "jpg"
                val (_, out) = SaveUtil.create(this, "social_${SaveUtil.stamp()}.$ext", if (isVideo) "video/mp4" else "image/jpeg", isVideo)
                var done = 0L
                conn.inputStream.use { inp ->
                    out.use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                runOnUiThread { bar.progress = p; info.text = "Downloading… $p%" }
                            }
                        }
                    }
                }
                runOnUiThread {
                    message("Saved ✔", "Saved to " + (if (isVideo) "Movies" else "Pictures") + "/SocialSaver. Open your Gallery to see it.")
                }
            } catch (e: Exception) {
                runOnUiThread { message("Download failed", e.message ?: "Unknown error") }
            }
        }.start()
    }
}
