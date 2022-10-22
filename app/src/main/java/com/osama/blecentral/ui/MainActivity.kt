package com.osama.blecentral.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.osama.blecentral.*
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
import com.osama.blecentral.bluetooth.BluetoothServiceManager.subscriptionStrResValue
import com.osama.blecentral.utils.BLELifecycleState
import com.osama.blecentral.databinding.ActivityMainBinding
import com.osama.blecentral.utils.activityResultHandlers
import com.osama.blecentral.utils.ensureBluetoothCanBeUsed
import com.osama.blecentral.utils.notifyForeGroundNotification
import com.osama.blecentral.utils.permissionResultHandlers
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*


@Suppress("UNUSED_PARAMETER", "DEPRECATION")
@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val userWantsToScanAndConnect: Boolean get() = binding.switchConnect.isChecked
    private val viewModel by viewModels<MainViewModel>()
    private var bleOnOffListener: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        appendLog("MainActivity.onCreate")
        initListeners()
        observers()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bleOnOffListener != null)
            unregisterReceiver(bleOnOffListener)
    }

    private fun observers() {
        log.observe(this) {
            appendLog(it)
        }

        lifecycleStateValue.observe(this) { value ->
            binding.textViewLifecycleState.text = value.name
            notifyForeGroundNotification(this, value.name)
            if (value != BLELifecycleState.Connected) {
                binding.textViewSubscription.text = getString(R.string.text_not_subscribed)
            }
            if (value == BLELifecycleState.Connected) {
                binding.switchConnect.isChecked = true
            }
        }

        restartLifecycle.observe(this) {
            if (it)
                viewModel.bleRestartLifecycle()
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
            }
        }
    }

    private fun initListeners() {
        binding.switchConnect.setOnClickListener {
            when (userWantsToScanAndConnect) {
                true -> {
                    if (bleOnOffListener == null)
                        initReceiver()
                    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    registerReceiver(bleOnOffListener, filter)
                }
                false -> {
                    if (bleOnOffListener != null) {
                        unregisterReceiver(bleOnOffListener)
                    }
                    viewModel.bleEndLifecycle()
                }
            }
            if (userWantsToScanAndConnect)
                prepareAndStartBleScan()
        }

    }

    fun onTapRead(view: View) {
        val gatt = connectedGatt ?: run {
            appendLog("ERROR: read failed, no connected device")
            return
        }
        val characteristic = characteristicForRead ?: run {
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
        val gatt = connectedGatt ?: run {
            appendLog("ERROR: write failed, no connected device")
            return
        }
        val characteristic = characteristicForWrite ?: run {
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

    @SuppressLint("SetTextI18n")
    fun onTapClearLog(view: View) {
        binding.textViewLog.text = "Logs:"
        appendLog("log cleared")
    }

    @SuppressLint("SetTextI18n")
    private fun appendLog(message: String) {
        Log.d("appendLog", message)
        runOnUiThread {
            val strTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            binding.textViewLog.text = binding.textViewLog.text.toString() + "\n$strTime $message"

            // scroll after delay, because textView has to be updated first
            Handler(Looper.getMainLooper()).postDelayed({
                binding.scrollViewLog.fullScroll(View.FOCUS_DOWN)
            }, 16)
        }
    }

    private fun initReceiver() {
        bleOnOffListener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.STATE_OFF
                )) {
                    BluetoothAdapter.STATE_ON -> {
                        appendLog("onReceive: Bluetooth ON")
                        if (lifecycleState == BLELifecycleState.Disconnected && userWantsToScanAndConnect) {
                            prepareAndStartBleScan()
                        }
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        appendLog("onReceive: Bluetooth OFF")
                        viewModel.bleEndLifecycle()
                        binding.switchConnect.isChecked = false
                    }
                }
            }
        }
    }

    private fun prepareAndStartBleScan() {
        ensureBluetoothCanBeUsed(viewModel, this) { isSuccess, message ->
            BluetoothServiceManager.logCallback.invoke(message)
            if (isSuccess) {
                viewModel.bleRestartLifecycle()
            }
        }
    }

    @Deprecated("Deprecated in Java")
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


    //endregion
}