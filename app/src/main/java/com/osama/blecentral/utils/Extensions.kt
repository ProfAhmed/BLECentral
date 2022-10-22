package com.osama.blecentral

import android.app.Activity
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

//region BluetoothGattCharacteristic extension
fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWriteable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    return (properties and property) != 0
}
//endregion

fun Context.hasPermissions(permissions: Array<String>): Boolean = permissions.all {
    ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

fun Activity.requestPermissionArray(permissions: Array<String>, requestCode: Int) {
    ActivityCompat.requestPermissions(this, permissions, requestCode)
}
