package com.thermalsentinel.engine.widget

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.thermalsentinel.R
import com.thermalsentinel.ThermalSentinelApp
import com.thermalsentinel.engine.service.MonitoringStartResult
import com.thermalsentinel.engine.service.MonitoringStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Quick Settings entry point for the same user-controlled monitoring controller
 * used by the app UI. The tile never writes the persisted monitoring preference
 * itself and never starts a second monitoring implementation.
 *
 * Tile state reflects the live service state, not merely the persisted intent.
 * This is important after Android/OEM process termination: a saved preference
 * must not make the tile claim that monitoring is currently active.
 */
class ThermalQuickSettingsTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actionMutex = Mutex()

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        // Ignore a second tap until the first asynchronous transition completes.
        if (!actionMutex.tryLock()) return
        scope.launch {
            try {
                val controller = (application as ThermalSentinelApp).graph.monitoring
                if (MonitoringStatus.isRunning.value) {
                    controller.stop()
                    waitForState(running = false)
                } else {
                    when (val result = controller.start()) {
                        MonitoringStartResult.Started -> waitForState(running = true)
                        is MonitoringStartResult.Rejected -> Toast.makeText(
                            this@ThermalQuickSettingsTileService,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Toast.makeText(
                    this@ThermalQuickSettingsTileService,
                    getString(R.string.qs_tile_action_failed),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                refreshTile()
                actionMutex.unlock()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun waitForState(running: Boolean) {
        // Service startup/shutdown is asynchronous. Bound the wait so a device or
        // OEM that refuses to run the service cannot leave the tile callback hung.
        repeat(20) {
            if (MonitoringStatus.isRunning.value == running) return
            delay(100)
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val running = MonitoringStatus.isRunning.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        tile.subtitle = getString(
            if (running) R.string.qs_tile_running else R.string.qs_tile_stopped
        )
        tile.contentDescription = getString(
            if (running) R.string.qs_tile_running_description
            else R.string.qs_tile_stopped_description
        )
        tile.stateDescription = getString(
            if (running) R.string.qs_tile_running else R.string.qs_tile_stopped
        )
        tile.updateTile()
    }
}
