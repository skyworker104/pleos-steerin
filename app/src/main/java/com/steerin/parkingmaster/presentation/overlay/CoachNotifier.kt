package com.steerin.parkingmaster.presentation.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.presentation.MainActivity

/**
 * Heads-up notification channel for coaching while another app (the system rear-view) owns the
 * screen. Verified on the Pleos_Connect_v2.1 AVD: a high-importance notification is rendered as a
 * heads-up banner on top of the rear-view activity. This is the review-friendly way to get a
 * glanceable instruction over the camera; it carries text + icon only, not the live ring.
 */
class CoachNotifier(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notif_channel_desc)
            setSound(null, null) // the voice coach already speaks; no extra chime
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    val canPost: Boolean
        get() = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private var lastInstruction: String? = null

    /**
     * Shows or updates the single coaching banner. A heads-up banner is only re-alerted when the
     * instruction text changes (phase change / snap-in), so snapped toggles do not spam the driver.
     */
    fun show(instruction: String, snapped: Boolean) {
        if (!canPost) return
        val changed = instruction != lastInstruction
        lastInstruction = instruction
        if (changed) nm.cancel(NOTIF_ID) // force a fresh heads-up for the new instruction
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_wheel)
            .setContentTitle(context.getString(if (snapped) R.string.notif_title_snapped else R.string.notif_title))
            .setContentText(instruction)
            .setStyle(Notification.BigTextStyle().bigText(instruction))
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setPriority(Notification.PRIORITY_HIGH)
            .setOnlyAlertOnce(!changed)
            .setOngoing(true)
            .setContentIntent(open)
            .setColor(if (snapped) 0xFF00E676.toInt() else 0xFF00AAD2.toInt())
            .build()
        nm.notify(NOTIF_ID, n)
    }

    fun dismiss() {
        lastInstruction = null
        nm.cancel(NOTIF_ID)
    }

    companion object {
        const val CHANNEL_ID = "coaching"
        const val NOTIF_ID = 1001
    }
}
