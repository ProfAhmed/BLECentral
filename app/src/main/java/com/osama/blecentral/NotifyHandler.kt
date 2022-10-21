package com.osama.blecentral

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

fun showNotification(context: Context, message: String) {

    val pendingIntent = PendingIntent.getActivity(
        context,
        Random.nextInt(),
        createNotificationIntent(context, message),
        getPendingIntentFlag()
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("New Message received")
        .setContentText(message)
        .setAutoCancel(true)
        .setLights(Color.RED, 1000, 1000)
        .setVibrate(longArrayOf(0, 400, 250, 400))
        .setContentIntent(pendingIntent)

    with(NotificationManagerCompat.from(context)) {
        notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

fun showForGroundNotification(context: Context,message: String): Notification {

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

private fun createNotificationIntent(
    context: Context,
    data: String
): Intent {
    val clazzName = MainActivity::class.java
    return Intent(context, clazzName).apply {
        try {
            putExtra("message", data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun getPendingIntentFlag(): Int {
    return when {
        android.os.Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_IMMUTABLE
        android.os.Build.VERSION.SDK_INT >= 23 -> PendingIntent.FLAG_UPDATE_CURRENT
        else -> PendingIntent.FLAG_CANCEL_CURRENT
    }
}

