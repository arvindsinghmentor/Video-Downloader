package com.socialsaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SaveUtil {
    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Creates an entry in Pictures/SocialSaver or Movies/SocialSaver. */
    fun create(ctx: Context, name: String, mime: String, video: Boolean): Pair<Uri, OutputStream> {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/SocialSaver"
            )
        }
        val coll = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = ctx.contentResolver.insert(coll, values) ?: throw Exception("Could not create file")
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw Exception("Could not open file")
        return uri to out
    }

    fun copyFile(ctx: Context, f: File, name: String, mime: String, video: Boolean) {
        val (_, out) = create(ctx, name, mime, video)
        f.inputStream().use { i -> out.use { o -> i.copyTo(o) } }
    }
}
