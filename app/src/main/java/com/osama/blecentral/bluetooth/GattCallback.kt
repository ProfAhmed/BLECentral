package com.osama.blecentral.bluetooth

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.osama.blecentral.*
import com.osama.blecentral.bluetooth.BluetoothServiceManager.characteristicForIndicate
import com.osama.blecentral.bluetooth.BluetoothServiceManager.characteristicForRead
import com.osama.blecentral.bluetooth.BluetoothServiceManager.characteristicForWrite
import com.osama.blecentral.bluetooth.BluetoothServiceManager.connectedGatt
import com.osama.blecentral.bluetooth.BluetoothServiceManager.indicationTextValue
import com.osama.blecentral.bluetooth.BluetoothServiceManager.lifecycleState
import com.osama.blecentral.bluetooth.BluetoothServiceManager.logCallback
import com.osama.blecentral.bluetooth.BluetoothServiceManager.readTextValue
import com.osama.blecentral.bluetooth.BluetoothServiceManager.restartLifecycle
import com.osama.blecentral.bluetooth.BluetoothServiceManager.setConnectedGattToNull
import com.osama.blecentral.bluetooth.BluetoothServiceManager.subscribeToIndications
import com.osama.blecentral.bluetooth.BluetoothServiceManager.subscriptionStrResValue
import com.osama.blecentral.bluetooth.utils.BLELifecycleState
import java.util.*

@SuppressLint("MissingPermission")
class GattCallback(private val context: Context) : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        // TODO: timeout timer: if this callback not called - disconnect(), wait 120ms, close()

        val deviceAddress = gatt.device.address

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logCallback.invoke("Connected to $deviceAddress")
                logCallback.invoke("Connected to $deviceAddress")

                // TODO: bonding state

                // recommended on UI thread https://punchthrough.com/android-ble-guide/
                Handler(Looper.getMainLooper()).post {
                    lifecycleState = BLELifecycleState.ConnectedDiscovering
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logCallback.invoke("Disconnected from $deviceAddress")
                setConnectedGattToNull()
                gatt.close()
                lifecycleState = BLELifecycleState.Disconnected
                restartLifecycle.postValue(true)
            }
        } else {
            // TODO: random error 133 - close() and try reconnect

            logCallback.invoke("ERROR: onConnectionStateChange status=$status deviceAddress=$deviceAddress, disconnecting")

            setConnectedGattToNull()
            gatt.close()
            lifecycleState = BLELifecycleState.Disconnected
            restartLifecycle.postValue(true)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        logCallback.invoke("onServicesDiscovered services.count=${gatt.services.size} status=$status")

        if (status == 129 /*GATT_INTERNAL_ERROR*/) {
            // it should be a rare case, this article recommends to disconnect:
            // https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
            logCallback.invoke("ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting")
            gatt.disconnect()
            return
        }

        val service = gatt.getService(UUID.fromString(SERVICE_UUID)) ?: run {
            logCallback.invoke("ERROR: Service not found $SERVICE_UUID, disconnecting")
            gatt.disconnect()
            return
        }

        connectedGatt = gatt
        characteristicForRead = service.getCharacteristic(UUID.fromString(CHAR_FOR_READ_UUID))
        characteristicForWrite = service.getCharacteristic(UUID.fromString(CHAR_FOR_WRITE_UUID))
        characteristicForIndicate = service.getCharacteristic(
            UUID.fromString(
                CHAR_FOR_INDICATE_UUID
            )
        )

        characteristicForIndicate?.let {
            lifecycleState = BLELifecycleState.ConnectedSubscribing
            subscribeToIndications(it, gatt)
        } ?: run {
            logCallback.invoke("WARN: characteristic not found $CHAR_FOR_INDICATE_UUID")
            lifecycleState = BLELifecycleState.Connected
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (characteristic.uuid == UUID.fromString(CHAR_FOR_READ_UUID)) {
            val strValue = characteristic.value.toString(Charsets.UTF_8)
            val log = "onCharacteristicRead " + when (status) {
                BluetoothGatt.GATT_SUCCESS -> "OK, value=\"$strValue\""
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                else -> "error $status"
            }
            logCallback.invoke(log)
            readTextValue.postValue(strValue)
        } else {
            logCallback.invoke("onCharacteristicRead unknown uuid $characteristic.uuid")
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (characteristic.uuid == UUID.fromString(CHAR_FOR_WRITE_UUID)) {
            val log: String = "onCharacteristicWrite " + when (status) {
                BluetoothGatt.GATT_SUCCESS -> "OK"
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "not allowed"
                BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "invalid length"
                else -> "error $status"
            }
            logCallback.invoke(log)
        } else {
            logCallback.invoke("onCharacteristicWrite unknown uuid $characteristic.uuid")
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == UUID.fromString(CHAR_FOR_INDICATE_UUID)) {
            val strValue = characteristic.value.toString(Charsets.UTF_8)
            logCallback.invoke("onCharacteristicChanged value=\"$strValue\"")
            Log.d("onCharacteristicChanged", "onCharacteristicChanged value=\"$strValue\"")
            indicationTextValue.value = strValue
            val notification = showForGroundNotification(context, strValue)

            val mNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.notify(NOTIFICATION_FOREGROUND_ID, notification)

        } else {
            logCallback.invoke("onCharacteristicChanged unknown uuid $characteristic.uuid")
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (descriptor.characteristic.uuid == UUID.fromString(CHAR_FOR_INDICATE_UUID)) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = descriptor.value
                val isSubscribed = value.isNotEmpty() && value[0].toInt() != 0
                val subscriptionRes = when (isSubscribed) {
                    true -> R.string.text_subscribed
                    false -> R.string.text_not_subscribed
                }
                logCallback.invoke("onDescriptorWrite $subscriptionRes")
                subscriptionStrResValue.postValue(subscriptionRes)

            } else {
                logCallback.invoke("ERROR: onDescriptorWrite status=$status uuid=${descriptor.uuid} char=${descriptor.characteristic.uuid}")
            }

            // subscription processed, consider connection is ready for use
            lifecycleState = BLELifecycleState.Connected
        } else {
            logCallback.invoke("onDescriptorWrite unknown uuid $descriptor.characteristic.uuid")
        }
    }


}