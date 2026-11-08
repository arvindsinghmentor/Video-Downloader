package com.socialsaver

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class ToggleTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (HelperService.instance == null) {
            Toast.makeText(this, "Turn on Social Saver in Accessibility first", Toast.LENGTH_LONG).show()
            openApp()
            return
        }
        Prefs.setOn(this, !Prefs.isOn(this))
        refresh()
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (Prefs.isOn(this@ToggleTileService) && HelperService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        else startActivityAndCollapse(i)
    }
}
