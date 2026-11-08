package com.socialsaver

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Small shared state so the floating button hides itself while dialogs / captures are active. */
object Bus {
    @Volatile var capturing = false
    @Volatile var dialogOpen = false
    var listener: (() -> Unit)? = null

    fun changed() { Handler(Looper.getMainLooper()).post { listener?.invoke() } }
    fun setCapturing(v: Boolean) { capturing = v; changed() }
    fun setDialog(v: Boolean) { dialogOpen = v; changed() }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun isOn(c: Context) = sp(c).getBoolean("on", false)
    fun setOn(c: Context, v: Boolean) { sp(c).edit().putBoolean("on", v).apply(); Bus.changed() }
}
