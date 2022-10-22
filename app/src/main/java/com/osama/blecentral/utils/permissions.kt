package com.osama.blecentral.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import com.osama.blecentral.*
import com.osama.blecentral.ui.MainActivity
import com.osama.blecentral.ui.MainViewModel

var activityResultHandlers = mutableMapOf<Int, (Int) -> Unit>()
var permissionResultHandlers =
    mutableMapOf<Int, (Array<out String>, IntArray) -> Unit>()

fun ensureBluetoothCanBeUsed(
    viewModel: MainViewModel, context: Activity, completion: (Boolean, String) -> Unit
) {
    grantBluetoothCentralPermissions(context, AskType.AskOnce) { isGranted ->
        if (!isGranted) {
            completion(false, "Bluetooth permissions denied")
            return@grantBluetoothCentralPermissions
        }

        enableBluetooth(viewModel, context, AskType.AskOnce) { isEnabled ->
            if (!isEnabled) {
                completion(false, "Bluetooth OFF")
                return@enableBluetooth
            }

            grantLocationPermissionIfRequired(context, AskType.AskOnce) { isGranted ->
                if (!isGranted) {
                    completion(false, "Location permission denied")
                    return@grantLocationPermissionIfRequired
                }

                completion(true, "Bluetooth ON, permissions OK, ready")
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun enableBluetooth(
    viewModel: MainViewModel, context: Activity, askType: AskType, completion: (Boolean) -> Unit
) {
    if (viewModel.bluetoothAdapter.isEnabled) {
        completion(true)
    } else {
        val intentString = BluetoothAdapter.ACTION_REQUEST_ENABLE
        val requestCode = ENABLE_BLUETOOTH_REQUEST_CODE

        // set activity result handler
        activityResultHandlers[requestCode] = { result ->
            Unit
            val isSuccess = result == Activity.RESULT_OK
            if (isSuccess || askType != AskType.InsistUntilSuccess) {
                activityResultHandlers.remove(requestCode)
                completion(isSuccess)
            } else {
                // start activity for the request again
                context.startActivityForResult(Intent(intentString), requestCode)
            }
        }

        // start activity for the request
        context.startActivityForResult(Intent(intentString), requestCode)
    }
}

private fun grantLocationPermissionIfRequired(
    context: Context, askType: AskType, completion: (Boolean) -> Unit
) {
    val wantedPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // BLUETOOTH_SCAN permission has flag "neverForLocation", so location not needed
        completion(true)
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.hasPermissions(
            wantedPermissions
        )
    ) {
        completion(true)
    } else {
        val requestCode = LOCATION_PERMISSION_REQUEST_CODE

        // prepare motivation message
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Location permission required")
        builder.setMessage("BLE advertising requires location access, starting from Android 6.0")
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            (context as MainActivity).requestPermissionArray(wantedPermissions, requestCode)
        }
        builder.setCancelable(false)

        // set permission result handler
        permissionResultHandlers[requestCode] = { permissions, grantResults ->
            val isSuccess = grantResults.firstOrNull() != PackageManager.PERMISSION_DENIED
            if (isSuccess || askType != AskType.InsistUntilSuccess) {
                permissionResultHandlers.remove(requestCode)
                completion(isSuccess)
            } else {
                // show motivation message again
                builder.create().show()
            }
        }

        // show motivation message
        builder.create().show()
    }
}

private fun grantBluetoothCentralPermissions(
    context: Context, askType: AskType, completion: (Boolean) -> Unit
) {
    val wantedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    } else {
        emptyArray()
    }

    if (wantedPermissions.isEmpty() || (context as MainActivity).hasPermissions(wantedPermissions)) {
        completion(true)
    } else {
        val requestCode = BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE

        // set permission result handler
        permissionResultHandlers[requestCode] = { _ /*permissions*/, grantResults ->
            val isSuccess = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (isSuccess || askType != AskType.InsistUntilSuccess) {
                permissionResultHandlers.remove(requestCode)
                completion(isSuccess)
            } else {
                // request again
                context.requestPermissionArray(wantedPermissions, requestCode)
            }
        }

        context.requestPermissionArray(wantedPermissions, requestCode)
    }

}
