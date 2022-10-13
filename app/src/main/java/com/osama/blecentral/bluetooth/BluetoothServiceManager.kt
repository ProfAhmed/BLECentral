package com.osama.blecentral.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.osama.blecentral.CCC_DESCRIPTOR_UUID
import com.osama.blecentral.bluetooth.utils.BLELifecycleState
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

@SuppressLint("MissingPermission")
object BluetoothServiceManager {
    private val _log = MutableLiveData<String>()
    val log = _log as LiveData<String>

    val logCallback: (String) -> Unit = {
        _log.postValue(it)
    }

    val lifecycleStateValue = MutableLiveData<BLELifecycleState>()
    val restartLifecycle = MutableLiveData<Boolean>()
    val readTextValue = MutableLiveData<String>()
    val subscriptionStrResValue = MutableLiveData<Int>()
    val indicationTextValue = MutableStateFlow<String?>(null)

    var lifecycleState = BLELifecycleState.Disconnected
        set(value) {
            field = value
            logCallback.invoke("status = ${value.name}")
            lifecycleStateValue.postValue(value)

        }

    var connectedGatt: BluetoothGatt? = null
    var characteristicForRead: BluetoothGattCharacteristic? = null
    var characteristicForWrite: BluetoothGattCharacteristic? = null
    var characteristicForIndicate: BluetoothGattCharacteristic? = null

    private var gattCallback: BluetoothGattCallback? = null

    // Properties for current chat device connection
    private var currentDevice: BluetoothDevice? = null


    fun subscribeToIndications(
        characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt
    ) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                logCallback.invoke("ERROR: setNotification(true) failed for ${characteristic.uuid}")
                return
            }
            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            gatt.writeDescriptor(cccDescriptor)
        }
    }

    fun setConnection(device: BluetoothDevice, context: Context) {
        currentDevice = device
        lifecycleState = BLELifecycleState.Connecting
        gattCallback = GattCallback()
        device.connectGatt(context, false, gattCallback)
    }

    fun setConnectedGattToNull() {
        connectedGatt = null
        characteristicForRead = null
        characteristicForWrite = null
        characteristicForIndicate = null
    }

}