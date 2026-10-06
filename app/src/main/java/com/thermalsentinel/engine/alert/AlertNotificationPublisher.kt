package com.thermalsentinel.engine.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thermalsentinel.R
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.ThermalFormatting
import com.thermalsentinel.ui.MainActivity

/**
 * All user-visible notifications the engine produces.
 *
 * Two channels, two different meanings:
 *
 *  - `monitoring` — the ongoing, silent notification that keeps the foreground
 *    service honest about what it is doing. It carries the live temperature so
 *    the user can see the value without opening the app.
 *  - `thermal_alerts` — warning/critical alerts. This one is allowed to make
 *    noise, and it is the only channel the cooldown gate controls.
 *
 * Every `notify` call goes through [notifyIfPermitted], which checks
 * `POST_NOTIFICATIONS` (API 33+) and `areNotificationsEnabled()` first. When the
 * permission is missing the app says so in the UI instead of assuming the user
 * can see the notification; monitoring itself continues to run.
 */
class AlertNotificationPublisher(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val monitoring = NotificationChannel(
            CHANNEL_MONITORING,
            context.getString(R.string.notification_channel_monitoring_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_monitoring_description)
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.notification_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alerts_description)
        }
        manager.createNotificationChannel(monitoring)
        manager.createNotificationChannel(alerts)
    }

    /** True when a notification posted now would actually be visible. */
    fun notificationsVisible(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun buildMonitoringNotification(
        sample: DeviceSample?,
        alertLevel: AlertLevel,
        updating: Boolean
    ): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val temperature = sample?.battery?.temperatureC?.valueOrNull
        val status = sample?.thermal?.thermalStatus?.valueOrNull
        val valueText = ThermalFormatting.temperatureCelsius(
            temperature,
            unavailable = context.getString(R.string.widget_temperature_unavailable)
        )
        val statusText = status?.label ?: UNAVAILABLE_STATUS
        val body = if (temperature == null && status == null) {
            context.getString(R.string.notification_monitoring_text_waiting)
        } else {
            context.getString(R.string.notification_monitoring_text, valueText, statusText)
        }
        val permissionNote = if (notificationsVisible()) {
            null
        } else {
            context.getString(R.string.notification_monitoring_permission_hidden)
        }
        val alertNote = if (alertLevel == AlertLevel.NORMAL) null else alertLevel.label
        val fullText = listOfNotNull(body, alertNote, permissionNote).joinToString(" ")

        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notification_monitoring_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setOngoing(true)
            .setOnlyAlertOnce(updating)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Refreshes the ongoing notification outside `startForeground`. */
    fun updateMonitoringNotification(notification: Notification) {
        notifyIfPermitted(ID_MONITORING, notification)
    }

    fun cancelMonitoringNotification() {
        manager.cancel(ID_MONITORING)
    }

    /**
     * Posts an alert. The temperature in the text is the measured value, and the
     * threshold quoted in the text is the threshold that actually fired (the
     * charging-specific one while charging, if it is enabled).
     */
    fun publishAlert(action: AlertAction.Notify, rules: ThermalAlertRules, onExternalPower: Boolean) {
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val temperatureText = ThermalFormatting.temperatureCelsius(action.temperatureC, unavailable = UNAVAILABLE_TEMPERATURE)
        val title: String
        val text: String
        when (action.trigger) {
            AlertTrigger.THERMAL_STATUS -> {
                title = context.getString(R.string.alert_status_title)
                text = context.getString(
                    R.string.alert_status_text,
                    action.thermalStatus?.label ?: UNAVAILABLE_STATUS
                )
            }
            AlertTrigger.CHARGING_TEMPERATURE -> {
                title = context.getString(R.string.alert_warning_title)
                text = context.getString(
                    R.string.alert_warning_text,
                    temperatureText,
                    ThermalFormatting.temperatureCelsius(rules.effectiveWarningThresholdC(onExternalPower))
                )
            }
            AlertTrigger.BATTERY_TEMPERATURE -> {
                if (action.level == AlertLevel.CRITICAL) {
                    title = context.getString(R.string.alert_critical_title)
                    text = context.getString(
                        R.string.alert_critical_text,
                        temperatureText,
                        ThermalFormatting.temperatureCelsius(rules.criticalThresholdC)
                    )
                } else {
                    title = context.getString(R.string.alert_warning_title)
                    text = context.getString(
                        R.string.alert_warning_text,
                        temperatureText,
                        ThermalFormatting.temperatureCelsius(rules.effectiveWarningThresholdC(onExternalPower))
                    )
                }
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOnlyAlertOnce(action.isReminder)
            .setContentIntent(openApp)
            .build()

        notifyIfPermitted(ID_ALERT, notification)
    }

    fun publishClearedNotification(action: AlertAction.Cleared, rules: ThermalAlertRules) {
        val text = context.getString(
            R.string.alert_cleared_text,
            ThermalFormatting.temperatureCelsius(action.temperatureC, unavailable = UNAVAILABLE_TEMPERATURE)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.alert_cleared_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        // Deliberately not gated by the alert cooldown: "you are back in range"
        // is the message that stops a user from worrying after an alert.
        notifyIfPermitted(ID_ALERT_CLEARED, notification)
        manager.cancel(ID_ALERT)
    }

    fun cancelAlertNotification() {
        manager.cancel(ID_ALERT)
    }

    private fun notifyIfPermitted(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Notification $id suppressed: POST_NOTIFICATIONS is not granted.")
            return
        }
        if (!manager.areNotificationsEnabled()) {
            Log.i(TAG, "Notification $id suppressed: notifications are disabled for the app.")
            return
        }
        try {
            manager.notify(id, notification)
        } catch (security: SecurityException) {
            // Notification privilege can be revoked between the check and the post.
            Log.w(TAG, "Notification $id was rejected by the platform", security)
        }
    }

    companion object {
        private const val TAG = "AlertNotifications"

        const val CHANNEL_MONITORING = "monitoring"
        const val CHANNEL_ALERTS = "thermal_alerts"

        const val ID_MONITORING = 4101
        const val ID_ALERT = 4102
        const val ID_ALERT_CLEARED = 4103

        private const val REQUEST_OPEN_APP = 9001

        private const val UNAVAILABLE_TEMPERATURE = "temperature unavailable"
        private const val UNAVAILABLE_STATUS = "thermal status unavailable"
    }
}
