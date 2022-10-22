package com.osama.blecentral.bluetooth.background

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.osama.blecentral.NOTIFICATION_FOREGROUND_ID
import com.osama.blecentral.bluetooth.BluetoothServiceManager
import com.osama.blecentral.utils.Actions
import com.osama.blecentral.utils.showForGroundNotification

class BleGattService : Service() {
    private val TAG = "BleService"

    override fun onCreate() {
        super.onCreate()
        startForeground(this, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Action Received = ${intent?.action}")
        when (intent?.action) {
            Actions.START_FOREGROUND -> {
                startForeground(this, BluetoothServiceManager.currentDevice)
            }
            Actions.STOP_FOREGROUND -> {
                stopForegroundService()
            }
        }
        return START_STICKY
    }

    private fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }

    private fun startForeground(context: Context, device: BluetoothDevice?) {

        var notification = showForGroundNotification(context, "empty message")

        startForeground(NOTIFICATION_FOREGROUND_ID, notification)

        //connect
        if (device != null) {
            notification =
                showForGroundNotification(context, BluetoothServiceManager.lifecycleState.name)
            startForeground(NOTIFICATION_FOREGROUND_ID, notification)
            BluetoothServiceManager.setConnection(device, context)
        }
    }


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}