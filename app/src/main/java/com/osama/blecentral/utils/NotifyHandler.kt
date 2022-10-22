package com.osama.blecentral.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.osama.blecentral.CHANNEL_ID
import com.osama.blecentral.NOTIFICATION_FOREGROUND_ID
import com.osama.blecentral.R
import com.osama.blecentral.ui.MainActivity

fun showForGroundNotification(context: Context, message: String): Notification {

    val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
    val notificationIntent = Intent(context, MainActivity::class.java)
    val pendingIntent: PendingIntent =
        PendingIntent.getActivity(context, 0, notificationIntent, getPendingIntentFlag())
    return notificationBuilder.setOngoing(true)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Service is running in background")
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setContentIntent(pendingIntent)
        .build()
}


fun notifyForeGroundNotification(context: Context, str: String) {
    val notification = showForGroundNotification(context, str)

    val mNotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mNotificationManager.notify(NOTIFICATION_FOREGROUND_ID, notification)
}

private fun getPendingIntentFlag(): Int {
    return when {
        android.os.Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_IMMUTABLE
        android.os.Build.VERSION.SDK_INT >= 23 -> PendingIntent.FLAG_UPDATE_CURRENT
        else -> PendingIntent.FLAG_CANCEL_CURRENT
    }


}

