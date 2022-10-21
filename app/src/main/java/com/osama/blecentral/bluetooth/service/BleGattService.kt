package com.osama.blecentral.bluetooth.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.osama.blecentral.NOTIFICATION_FOREGROUND_ID
import com.osama.blecentral.bluetooth.BluetoothServiceManager
import com.osama.blecentral.bluetooth.utils.Actions
import com.osama.blecentral.showForGroundNotification

@SuppressLint("MissingPermission")
class BleGattService : Service() {
    private val TAG = "BleService"

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

        val notification = showForGroundNotification(context, "empty message")

        startForeground(NOTIFICATION_FOREGROUND_ID, notification)

        //connect
        BluetoothServiceManager.setConnection(device, context)
    }


    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}