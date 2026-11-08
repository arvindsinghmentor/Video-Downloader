package com.socialsaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var reader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var video = false
    private var finished = false
    private var outFile: File? = null

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) { stopSelf(); return START_NOT_STICKY }
        if (i.action == "STOP") { if (video) finishVideo() else stopSelf(); return START_NOT_STICKY }

        video = i.getStringExtra("mode") == "video"
        startFg()
        val quality = i.getIntExtra("quality", 100)
        val seconds = i.getIntExtra("seconds", 30)
        val data = i.getParcelableExtra<Intent>("data")
        if (data == null) { fail("Permission data missing"); return START_NOT_STICKY }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(i.getIntExtra("code", 0), data)
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { handler.post { if (!finished && video) finishVideo() } }
        }, handler)

        // give Instagram/Facebook a moment to come back to the front
        handler.postDelayed({ if (video) startVideo(quality, seconds) else takePhoto(quality) }, 900)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun screen(): Triple<Int, Int, Int> {
        val m = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        return Triple(m.widthPixels, m.heightPixels, m.densityDpi)
    }

    // ---------- Photo ----------

    private fun takePhoto(scale: Int) {
        try {
            val (w, h, dpi) = screen()
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            vd = projection!!.createVirtualDisplay(
                "cap", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
            )
            handler.postDelayed({ grab(w, h, scale, 0) }, 600)
        } catch (e: Exception) { fail("Capture failed: ${e.message}") }
    }

    private fun grab(w: Int, h: Int, scale: Int, attempt: Int) {
        if (finished) return
        val img = reader?.acquireLatestImage()
        if (img == null) {
            if (attempt < 6) handler.postDelayed({ grab(w, h, scale, attempt + 1) }, 300) else fail("No frame received")
            return
        }
        try {
            val plane = img.planes[0]
            val rowPad = plane.rowStride - plane.pixelStride * w
            val full = Bitmap.createBitmap(w + rowPad / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            img.close()
            val cropped = Bitmap.createBitmap(full, 0, 0, w, h)
            val out = if (scale < 100) Bitmap.createScaledBitmap(cropped, w * scale / 100, h * scale / 100, true) else cropped
            val f = File(cacheDir, "capture.jpg")
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            done(f, false)
        } catch (e: Exception) { fail("Capture failed: ${e.message}") }
    }

    // ---------- Video ----------

    private fun startVideo(quality: Int, seconds: Int) {
        try {
            val (w0, h0, dpi) = screen()
            val sc = min(1f, quality.toFloat() / min(w0, h0))
            val w = (w0 * sc).toInt() and 1.inv()
            val h = (h0 * sc).toInt() and 1.inv()
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(w, h)
            rec.setVideoFrameRate(30)
            rec.setVideoEncodingBitRate(if (quality >= 1080) 10_000_000 else if (quality >= 720) 6_000_000 else 3_000_000)
            val f = File(cacheDir, "capture.mp4"); outFile = f
            rec.setOutputFile(f.absolutePath)
            rec.prepare()
            vd = projection!!.createVirtualDisplay(
                "rec", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, handler
            )
            rec.start()
            recorder = rec
            handler.postDelayed({ finishVideo() }, seconds * 1000L)
        } catch (e: Exception) { fail("Recording failed: ${e.message}") }
    }

    private fun finishVideo() {
        if (finished) return
        val rec = recorder ?: run { fail("Recording not started"); return }
        recorder = null
        try { rec.stop() } catch (e: Exception) { rec.release(); fail("Recording was too short"); return }
        rec.release()
        done(outFile!!, true)
    }

    // ---------- Finish ----------

    private fun cleanup() {
        runCatching { vd?.release() }
        runCatching { reader?.close() }
        runCatching { recorder?.release() }
        runCatching { projection?.stop() }
        vd = null; reader = null; recorder = null
    }

    private fun done(f: File, isVideo: Boolean) {
        if (finished) return
        finished = true
        cleanup()
        Bus.setCapturing(false)
        val i = Intent(this, PreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("path", f.absolutePath).putExtra("video", isVideo)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ready", "Capture ready", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(2, Notification.Builder(this, "ready").setSmallIcon(R.drawable.ic_ring)
            .setContentTitle("Capture ready").setContentText("Tap to preview and save")
            .setContentIntent(pi).setAutoCancel(true).build())
        runCatching { startActivity(i) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(msg: String) {
        finished = true
        cleanup()
        Bus.setCapturing(false)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); cleanup(); super.onDestroy() }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cap", "Capturing", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CaptureService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "cap").setSmallIcon(R.drawable.ic_ring)
            .setContentTitle(if (video) "Recording screen…" else "Capturing screen…")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }
}
