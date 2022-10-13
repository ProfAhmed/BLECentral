package com.osama.blecentral

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.osama.blecentral.bluetooth.BluetoothServiceManager
import com.osama.blecentral.bluetooth.BluetoothServiceManager.characteristicForRead
import com.osama.blecentral.bluetooth.BluetoothServiceManager.characteristicForWrite
import com.osama.blecentral.bluetooth.BluetoothServiceManager.connectedGatt
import com.osama.blecentral.bluetooth.BluetoothServiceManager.indicationTextValue
import com.osama.blecentral.bluetooth.BluetoothServiceManager.lifecycleState
import com.osama.blecentral.bluetooth.BluetoothServiceManager.lifecycleStateValue
import com.osama.blecentral.bluetooth.BluetoothServiceManager.log
import com.osama.blecentral.bluetooth.BluetoothServiceManager.readTextValue
import com.osama.blecentral.bluetooth.BluetoothServiceManager.restartLifecycle
import com.osama.blecentral.bluetooth.BluetoothServiceManager.setConnectedGattToNull
import com.osama.blecentral.bluetooth.BluetoothServiceManager.subscriptionStrResValue
import com.osama.blecentral.bluetooth.utils.AskType
import com.osama.blecentral.bluetooth.utils.BLELifecycleState
import com.osama.blecentral.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val userWantsToScanAndConnect: Boolean get() = binding.switchConnect.isChecked
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        appendLog("MainActivity.onCreate")
        initListeners()
        observers()

    }

    private fun observers() {
        log.observe(this) {
            appendLog(it)
        }

        lifecycleStateValue.observe(this) { value ->
            binding.textViewLifecycleState.text = value.name
            if (value != BLELifecycleState.Connected) {
                binding.textViewSubscription.text = getString(R.string.text_not_subscribed)
            }
        }

        restartLifecycle.observe(this) {
            if (it)
                bleRestartLifecycle()
        }

        subscriptionStrResValue.observe(this) {
            binding.textViewSubscription.text = getString(it)
        }

        readTextValue.observe(this) {
            binding.textViewReadValue.text = it
        }

        lifecycleScope.launchWhenResumed {
            indicationTextValue.collectLatest {
                it ?: return@collectLatest
                binding.textViewIndicateValue.text = it
                showNotification(this@MainActivity, it)
            }
        }
    }

    private fun initListeners() {
        binding.switchConnect.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> {
                    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    registerReceiver(bleOnOffListener, filter)
                }
                false -> {
                    unregisterReceiver(bleOnOffListener)
                }
            }
            bleRestartLifecycle()
        }
    }

    override fun onDestroy() {
        bleEndLifecycle()
        super.onDestroy()
    }

    fun onTapRead(view: View) {
        var gatt = connectedGatt ?: run {
            appendLog("ERROR: read failed, no connected device")
            return
        }
        var characteristic = characteristicForRead ?: run {
            appendLog("ERROR: read failed, characteristic unavailable $CHAR_FOR_READ_UUID")
            return
        }
        if (!characteristic.isReadable()) {
            appendLog("ERROR: read failed, characteristic not readable $CHAR_FOR_READ_UUID")
            return
        }
        gatt.readCharacteristic(characteristic)
    }

    fun onTapWrite(view: View) {
        var gatt = connectedGatt ?: run {
            appendLog("ERROR: write failed, no connected device")
            return
        }
        var characteristic = characteristicForWrite ?: run {
            appendLog("ERROR: write failed, characteristic unavailable $CHAR_FOR_WRITE_UUID")
            return
        }
        if (!characteristic.isWriteable()) {
            appendLog("ERROR: write failed, characteristic not writeable $CHAR_FOR_WRITE_UUID")
            return
        }
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value =
            binding.editTextWriteValue.text.toString().toByteArray(Charsets.UTF_8)
        gatt.writeCharacteristic(characteristic)
    }

    fun onTapClearLog(view: View) {
        binding.textViewLog.text = "Logs:"
        appendLog("log cleared")
    }

    private fun appendLog(message: String) {
        Log.d("appendLog", message)
        runOnUiThread {
            val strTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            binding.textViewLog.text = binding.textViewLog.text.toString() + "\n$strTime $message"

            // scroll after delay, because textView has to be updated first
            Handler().postDelayed({
                binding.scrollViewLog.fullScroll(View.FOCUS_DOWN)
            }, 16)
        }
    }

    private fun bleEndLifecycle() {
        safeStopBleScan()
        connectedGatt?.close()
        setConnectedGattToNull()
        lifecycleState = BLELifecycleState.Disconnected
    }

    private fun bleRestartLifecycle() {
        runOnUiThread {
            if (userWantsToScanAndConnect) {
                if (connectedGatt == null) {
                    prepareAndStartBleScan()
                } else {
                    connectedGatt?.disconnect()
                }
            } else {
                bleEndLifecycle()
            }
        }
    }

    private fun prepareAndStartBleScan() {
        ensureBluetoothCanBeUsed { isSuccess, message ->
            appendLog(message)
            if (isSuccess) {
                safeStartBleScan()
            }
        }
    }

    private fun safeStartBleScan() {
        if (isScanning) {
            appendLog("Already scanning")
            return
        }

        val serviceFilter = scanFilter.serviceUuid?.uuid.toString()
        appendLog("Starting BLE scan, filter: $serviceFilter")

        isScanning = true
        lifecycleState = BLELifecycleState.Scanning
        bleScanner.startScan(mutableListOf(scanFilter), scanSettings, scanCallback)
    }

    private fun safeStopBleScan() {
        if (!isScanning) {
            appendLog("Already stopped")
            return
        }

        appendLog("Stopping BLE scan")
        isScanning = false
        bleScanner.stopScan(scanCallback)
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    //region BLE Scanning
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
            appendLog("onScanResult name=$name address= ${result.device?.address}")
            safeStopBleScan()
            BluetoothServiceManager.setConnection(result.device, this@MainActivity)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            appendLog("onBatchScanResults, ignoring")
        }

        override fun onScanFailed(errorCode: Int) {
            appendLog("onScanFailed errorCode=$errorCode")
            safeStopBleScan()
            lifecycleState = BLELifecycleState.Disconnected
            bleRestartLifecycle()
        }
    }
    //endregion


    private var activityResultHandlers = mutableMapOf<Int, (Int) -> Unit>()
    private var permissionResultHandlers =
        mutableMapOf<Int, (Array<out String>, IntArray) -> Unit>()
    private var bleOnOffListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)) {
                BluetoothAdapter.STATE_ON -> {
                    appendLog("onReceive: Bluetooth ON")
                    if (lifecycleState == BLELifecycleState.Disconnected) {
                        bleRestartLifecycle()
                    }
                }
                BluetoothAdapter.STATE_OFF -> {
                    appendLog("onReceive: Bluetooth OFF")
                    bleEndLifecycle()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        activityResultHandlers[requestCode]?.let { handler ->
            handler(resultCode)
        } ?: runOnUiThread {
            appendLog("ERROR: onActivityResult requestCode=$requestCode result=$resultCode not handled")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionResultHandlers[requestCode]?.let { handler ->
            handler(permissions, grantResults)
        } ?: runOnUiThread {
            appendLog("ERROR: onRequestPermissionsResult requestCode=$requestCode not handled")
        }
    }

    private fun ensureBluetoothCanBeUsed(completion: (Boolean, String) -> Unit) {
        grantBluetoothCentralPermissions(AskType.AskOnce) { isGranted ->
            if (!isGranted) {
                completion(false, "Bluetooth permissions denied")
                return@grantBluetoothCentralPermissions
            }

            enableBluetooth(AskType.AskOnce) { isEnabled ->
                if (!isEnabled) {
                    completion(false, "Bluetooth OFF")
                    return@enableBluetooth
                }

                grantLocationPermissionIfRequired(AskType.AskOnce) { isGranted ->
                    if (!isGranted) {
                        completion(false, "Location permission denied")
                        return@grantLocationPermissionIfRequired
                    }

                    completion(true, "Bluetooth ON, permissions OK, ready")
                }
            }
        }
    }

    private fun enableBluetooth(askType: AskType, completion: (Boolean) -> Unit) {
        if (bluetoothAdapter.isEnabled) {
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
                    startActivityForResult(Intent(intentString), requestCode)
                }
            }

            // start activity for the request
            startActivityForResult(Intent(intentString), requestCode)
        }
    }

    private fun grantLocationPermissionIfRequired(askType: AskType, completion: (Boolean) -> Unit) {
        val wantedPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // BLUETOOTH_SCAN permission has flag "neverForLocation", so location not needed
            completion(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasPermissions(wantedPermissions)) {
            completion(true)
        } else {
            runOnUiThread {
                val requestCode = LOCATION_PERMISSION_REQUEST_CODE

                // prepare motivation message
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Location permission required")
                builder.setMessage("BLE advertising requires location access, starting from Android 6.0")
                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissionArray(wantedPermissions, requestCode)
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
    }

    private fun grantBluetoothCentralPermissions(askType: AskType, completion: (Boolean) -> Unit) {
        val wantedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            emptyArray()
        }

        if (wantedPermissions.isEmpty() || hasPermissions(wantedPermissions)) {
            completion(true)
        } else {
            runOnUiThread {
                val requestCode = BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE

                // set permission result handler
                permissionResultHandlers[requestCode] = { _ /*permissions*/, grantResults ->
                    val isSuccess = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (isSuccess || askType != AskType.InsistUntilSuccess) {
                        permissionResultHandlers.remove(requestCode)
                        completion(isSuccess)
                    } else {
                        // request again
                        requestPermissionArray(wantedPermissions, requestCode)
                    }
                }

                requestPermissionArray(wantedPermissions, requestCode)
            }
        }
    }

    private fun Context.hasPermissions(permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermissionArray(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }
    //endregion
}