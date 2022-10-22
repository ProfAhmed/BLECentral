package com.osama.blecentral.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import com.osama.blecentral.SERVICE_UUID
import com.osama.blecentral.bluetooth.BluetoothServiceManager
import com.osama.blecentral.bluetooth.BluetoothServiceManager.logCallback
import com.osama.blecentral.bluetooth.background.BleGattService
import com.osama.blecentral.utils.Actions
import com.osama.blecentral.utils.BLELifecycleState
import java.util.*

@SuppressLint("MissingPermission")
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mApplication = application
    private var isScanning = false

    //region BLE Scanning
    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            mApplication.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
        .build()

    private val scanSettings: ScanSettings
        get() {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                scanSettingsSinceM
            } else {
                scanSettingsBeforeM
            }
        }

    private val scanSettingsBeforeM = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setReportDelay(0)
        .build()

    @RequiresApi(Build.VERSION_CODES.M)
    private val scanSettingsSinceM = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        .setReportDelay(0)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name: String? = result.scanRecord?.deviceName ?: result.device.name
            logCallback.invoke("onScanResult name=$name address= ${result.device?.address}")
            safeStopBleScan()
            startForegroundService(result.device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            logCallback.invoke("onBatchScanResults, ignoring")
        }

        override fun onScanFailed(errorCode: Int) {
            logCallback.invoke("onScanFailed errorCode=$errorCode")
            safeStopBleScan()
            BluetoothServiceManager.lifecycleState = BLELifecycleState.Disconnected
            bleRestartLifecycle()
        }
    }
    //endregion

    private fun safeStartBleScan() {
        if (isScanning) {
            logCallback.invoke("Already scanning")
            return
        }

        val serviceFilter = scanFilter.serviceUuid?.uuid.toString()
        logCallback.invoke("Starting BLE scan, filter: $serviceFilter")

        isScanning = true
        BluetoothServiceManager.lifecycleState = BLELifecycleState.Scanning
        bleScanner.startScan(mutableListOf(scanFilter), scanSettings, scanCallback)
    }

    private fun safeStopBleScan() {
        if (!isScanning) {
            logCallback.invoke("Already stopped")
            return
        }

        logCallback.invoke("Stopping BLE scan")
        isScanning = false
        if (bluetoothAdapter.isEnabled)
            bleScanner.stopScan(scanCallback)
    }


    fun bleEndLifecycle() {
        safeStopBleScan()
        BluetoothServiceManager.connectedGatt?.close()
        BluetoothServiceManager.setConnectedGattToNull()
        BluetoothServiceManager.lifecycleState = BLELifecycleState.Disconnected
        stopForegroundService()
    }

    fun bleRestartLifecycle() {
        if (BluetoothServiceManager.connectedGatt == null) {
            safeStartBleScan()
        } else {
            BluetoothServiceManager.connectedGatt?.disconnect()
        }
    }

    private fun startForegroundService(device: BluetoothDevice) {
        //start connection in background
        Intent(mApplication, BleGattService::class.java).also { intent ->
            intent.action = Actions.START_FOREGROUND
            BluetoothServiceManager.currentDevice = device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mApplication.startForegroundService(intent)
            } else {
                mApplication.startService(intent)
            }
        }
    }

    private fun stopForegroundService() {
        Intent(mApplication, BleGattService::class.java).also { intent ->
            intent.action = Actions.STOP_FOREGROUND
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mApplication.startForegroundService(intent)
            } else {
                mApplication.startService(intent)
            }
        }
    }

}