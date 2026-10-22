package com.thermalsentinel.engine.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.thermalsentinel.R

/**
 * Quick Settings entry point for Thermal Sentinel.
 *
 * A tile click opens the app so monitoring can be started or stopped from a
 * foreground UI. It intentionally does not start a foreground service directly
 * from this system-owned callback: Android may reject that background start.
 * The tile is a shortcut, not a claim that monitoring is currently running.
 */
class ThermalQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.quick_settings_tile_label)
            subtitle = getString(R.string.quick_settings_tile_subtitle)
            icon = Icon.createWithResource(this@ThermalQuickSettingsTileService, R.mipmap.ic_launcher)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(launchIntent)
    }
}
