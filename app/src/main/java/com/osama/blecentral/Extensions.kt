package com.osama.blecentral

import android.bluetooth.BluetoothGattCharacteristic

//region BluetoothGattCharacteristic extension
fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWriteable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    return (properties and property) != 0
}
//endregion
