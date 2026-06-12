package com.example.rockmacro.hid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.rockmacro.notification.LiveNotificationManager
import java.util.UUID

class BluetoothHidService : Service() {

    companion object {
        private const val TAG = "BluetoothHidService"

        const val DEVICE_NAME = "RockMacro HID"
        const val DEVICE_DESCRIPTION = "Keyboard & Mouse Macro"
        const val DEVICE_PROVIDER = "RockMacro"
        const val DEVICE_SUBCLASS = 0x08
        const val DEVICE_COUNTRY = 0x21

        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805F9B34FB")
        val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "rockmacro_hid_channel"

        // 宏通知
        private const val MACRO_NOTIFICATION_ID = 1002

        const val ACTION_MACRO_PAUSE = "com.example.rockmacro.action.MACRO_PAUSE"
        const val ACTION_MACRO_STOP = "com.example.rockmacro.action.MACRO_STOP"
        const val ACTION_MACRO_RESUME = "com.example.rockmacro.action.MACRO_RESUME"

        private const val HEARTBEAT_INTERVAL_MS = 2000L
        private const val RECONNECT_DELAY_MS = 2000L

        @Volatile
        var instance: BluetoothHidService? = null
            private set
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    private val binder = LocalBinder()

    enum class HidState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ADVERTISING,
        ERROR
    }

    enum class DeviceType {
        KEYBOARD,
        MOUSE,
        COMPOSITE
    }

    data class ConnectedDevice(
        val device: BluetoothDevice,
        val type: DeviceType
    )

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var appSdpSettings: BluetoothHidDeviceAppSdpSettings? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var hidAppRegistered = false
    private var originalBluetoothName: String? = null

    private val _hidState = MutableStateFlow(HidState.DISCONNECTED)
    val hidState: StateFlow<HidState> = _hidState

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices

    private var registeredDevices = mutableSetOf<BluetoothDevice>()

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastConnectedDevice: BluetoothDevice? = null

    // 宏控制通知
    private var macroControlCallback: ((action: String) -> Unit)? = null
    private var macroNotificationShown = false

    private val macroNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_MACRO_PAUSE -> macroControlCallback?.invoke("pause")
                ACTION_MACRO_STOP -> macroControlCallback?.invoke("stop")
                ACTION_MACRO_RESUME -> macroControlCallback?.invoke("resume")
            }
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    if (state == BluetoothAdapter.STATE_ON) {
                        initHidDevice()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        _hidState.value = HidState.DISCONNECTED
                        stopAdvertising()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { dev ->
                        Log.d(TAG, "ACL connected: ${dev.address} name=${dev.name}")
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { onDeviceDisconnected(it) }
                }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: device=${pluggedDevice?.address}, registered=$registered")
            if (registered) {
                hidAppRegistered = true
                pluggedDevice?.let { device ->
                    Log.d(TAG, "Device registered: ${device.address}")
                }
            } else {
                pluggedDevice?.let { device ->
                    Log.d(TAG, "Device unregistered: ${device.address}")
                }
                // 如果所有设备都断开且没有已注册设备，重置状态
                if (_connectedDevices.value.isEmpty()) {
                    hidAppRegistered = false
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device?.address}, state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    device?.let { dev ->
                        Log.d(TAG, "HID connected: ${dev.address} name=${dev.name}")
                        _hidState.value = HidState.CONNECTED
                        addConnectedDevice(dev)
                        lastConnectedDevice = dev
                        updateNotification("Connected to: ${dev.name ?: dev.address}")
                        startHeartbeat()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    device?.let { dev ->
                        Log.d(TAG, "HID disconnected: ${dev.address}")
                        onDeviceDisconnected(dev)
                        stopHeartbeat()
                        updateNotification("Disconnected — attempting reconnect...")
                        scheduleReconnect(dev)
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d(TAG, "HID connecting: ${device?.address}")
                    _hidState.value = HidState.CONNECTING
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            device?.let { dev ->
                Log.d(TAG, "Get report request from ${dev.address}, type=$type, id=$id")
                bluetoothHidDevice?.replyReport(dev, type, id, ByteArray(bufferSize))
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            device?.let { dev ->
                Log.d(TAG, "Set report from ${dev.address}, type=$type, id=$id")
                if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && data != null) {
                    bluetoothHidDevice?.replyReport(dev, type, id, data)
                }
            }
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            device?.let { dev ->
                Log.d(TAG, "Set protocol from ${dev.address}: $protocol")
            }
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            device?.let { dev ->
                Log.d(TAG, "Interrupt data from ${dev.address}, reportId=$reportId")
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            device?.let { dev ->
                Log.d(TAG, "Virtual cable unplug: ${dev.address}")
                onDeviceDisconnected(dev)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothHidService created")
        instance = this

        createNotificationChannel()
        createMacroNotificationChannel()
        startForegroundService()
        acquireWakeLock()

        // 注册宏通知广播接收器
        val macroFilter = IntentFilter().apply {
            addAction(ACTION_MACRO_PAUSE)
            addAction(ACTION_MACRO_STOP)
            addAction(ACTION_MACRO_RESUME)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(macroNotificationReceiver, macroFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(macroNotificationReceiver, macroFilter)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothStateReceiver, filter)

        initHidDevice()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RockMacro HID Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for Bluetooth HID connection"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val intent = Intent().setClassName(this, "com.example.rockmacro.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RockMacro HID")
                .setContentText("Bluetooth HID connected — keyboard & mouse active")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RockMacro HID")
                .setContentText("Bluetooth HID connected — keyboard & mouse active")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        }

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(contentText: String) {
        val intent = Intent().setClassName(this, "com.example.rockmacro.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RockMacro HID")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RockMacro HID")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    // ==================== 宏播放通知 ====================

    private fun createMacroNotificationChannel() {
        LiveNotificationManager.createLiveUpdateChannel(this)
    }

    fun setMacroControlCallback(callback: ((action: String) -> Unit)?) {
        macroControlCallback = callback
    }

    fun showMacroNotification(isPlaying: Boolean, macroName: String) {
        macroNotificationShown = true

        Log.d(TAG, "显示宏通知: isPlaying=$isPlaying, macro=$macroName")

        val intent = Intent().setClassName(this, "com.example.rockmacro.MainActivity").apply {
            putExtra("navigate_to_tab", 3)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val pauseIntent = PendingIntent.getBroadcast(
            this, 10, Intent(ACTION_MACRO_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 11, Intent(ACTION_MACRO_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val resumeIntent = PendingIntent.getBroadcast(
            this, 12, Intent(ACTION_MACRO_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // 使用 IMPORTANCE_HIGH 渠道构建实况通知
        val notification = LiveNotificationManager.buildRunningNotification(
            context = this,
            macroName = macroName,
            isPlaying = isPlaying,
            pendingIntent = pendingIntent,
            pauseIntent = pauseIntent,
            resumeIntent = resumeIntent,
            stopIntent = stopIntent
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(MACRO_NOTIFICATION_ID, notification)
    }

    fun dismissMacroNotification() {
        if (!macroNotificationShown) return
        macroNotificationShown = false
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(MACRO_NOTIFICATION_ID)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RockMacro:HidWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minute timeout, auto-renewed
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun renewWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.acquire(10 * 60 * 1000L) // renew
            } else {
                it.acquire(10 * 60 * 1000L)
                Log.d(TAG, "WakeLock re-acquired")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun initHidDevice() {
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            _hidState.value = HidState.ERROR
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled")
            return
        }

        try {
            bluetoothAdapter?.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        bluetoothHidDevice = proxy as? BluetoothHidDevice
                        if (bluetoothHidDevice != null) {
                            Log.d(TAG, "Got BluetoothHidDevice proxy")
                            setupSdpRecord()
                        } else {
                            Log.e(TAG, "Failed to get BluetoothHidDevice proxy")
                            _hidState.value = HidState.ERROR
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        Log.d(TAG, "BluetoothHidDevice proxy disconnected")
                        bluetoothHidDevice = null
                        hidAppRegistered = false
                    }
                },
                BluetoothProfile.HID_DEVICE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting HID device proxy: ${e.message}")
            _hidState.value = HidState.ERROR
        }
    }

    private fun setupSdpRecord() {
        appSdpSettings = BluetoothHidDeviceAppSdpSettings(
            DEVICE_NAME,
            DEVICE_DESCRIPTION,
            DEVICE_PROVIDER,
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptors.compositeReportDescriptor
        )

        appSdpSettings?.let { sdp ->
            val result = bluetoothHidDevice?.registerApp(sdp, null, null, Runnable::run, hidCallback)
            if (result == true) {
                Log.d(TAG, "Composite HID app registered successfully")
                hidAppRegistered = true
            } else {
                Log.e(TAG, "Failed to register Composite HID app")
                _hidState.value = HidState.ERROR
            }
        }
    }

    fun startAdvertising(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        bleAdvertiser = adapter.bluetoothLeAdvertiser ?: return false

        // 保存原始蓝牙名称并改为 HID 设备名，让PC搜索时看到 "RockMacro HID"
        try {
            originalBluetoothName = adapter.name
            adapter.setName(DEVICE_NAME)
            Log.d(TAG, "Bluetooth name changed to: $DEVICE_NAME (was: $originalBluetoothName)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot change Bluetooth name: ${e.message}")
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(HID_SERVICE_UUID))
            .build()

        val scanResponseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(DEVICE_INFO_SERVICE_UUID))
            .addServiceUuid(ParcelUuid(BATTERY_SERVICE_UUID))
            .build()

        try {
            bleAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE advertising: ${e.message}")
            return false
        }
    }

    fun stopAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising: ${e.message}")
        }
        isAdvertising = false

        // 恢复原始蓝牙名称
        originalBluetoothName?.let { original ->
            try {
                bluetoothAdapter?.setName(original)
                Log.d(TAG, "Bluetooth name restored to: $original")
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot restore Bluetooth name: ${e.message}")
            }
        }
        originalBluetoothName = null
    }

    fun makeDiscoverable(): Boolean {
        if (!hidAppRegistered) {
            Log.e(TAG, "HID app not registered yet")
            return false
        }

        var success = false

        // BLE 广播（让支持BLE的PC也能发现此设备）
        if (bleAdvertiser != null) {
            success = startAdvertising()
            if (success) {
                Log.d(TAG, "BLE advertising started for HID device discovery")
            }
        } else {
            // 即使没有BLE，HID App注册后也应该是可连接的
            success = true
        }

        if (success) {
            _hidState.value = HidState.ADVERTISING
            Log.d(TAG, "Device is now discoverable as HID device (BLE advertising)")
        }
        return success
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE advertising started successfully")
            isAdvertising = true
            _hidState.value = HidState.ADVERTISING
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error: $errorCode")
            isAdvertising = false
        }
    }

    fun connectDevice(device: BluetoothDevice): Boolean {
        if (bluetoothHidDevice == null) {
            Log.e(TAG, "connectDevice failed: BluetoothHidDevice not initialized, proxy is null")
            _hidState.value = HidState.ERROR
            return false
        }

        if (!hidAppRegistered) {
            Log.e(TAG, "connectDevice failed: HID app not registered, trying to re-register...")
            setupSdpRecord()
            return false
        }

        try {
            stopAdvertising()
            Log.d(TAG, "Attempting to connect to device: ${device.address} (name=${device.name})")
            val result = bluetoothHidDevice?.connect(device)
            if (result == true) {
                Log.d(TAG, "connect() returned true for ${device.address}, waiting for callback...")
                _hidState.value = HidState.CONNECTING
                registeredDevices.add(device)
                return true
            } else {
                Log.e(TAG, "connect() returned false for ${device.address}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in connectDevice: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in connectDevice: ${e.message}", e)
        }

        return false
    }

    fun disconnectDevice(device: BluetoothDevice) {
        try {
            bluetoothHidDevice?.disconnect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    fun sendKeyboardReport(report: ByteArray): Boolean {
        val hid = bluetoothHidDevice ?: return false
        if (_hidState.value != HidState.CONNECTED) return false

        _connectedDevices.value.forEach { connected ->
            try {
                // report 已包含 Report ID 作为第一字节，传 id=0
                val result = hid.sendReport(connected.device, 0, report)
                if (!result) {
                    Log.w(TAG, "Failed to send keyboard report to ${connected.device.address}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                return false
            }
        }
        return true
    }

    fun sendMouseReport(report: ByteArray): Boolean {
        val hid = bluetoothHidDevice ?: return false
        if (_hidState.value != HidState.CONNECTED) return false

        _connectedDevices.value.forEach { connected ->
            try {
                // report 已包含 Report ID 作为第一字节，传 id=0
                val result = hid.sendReport(connected.device, 0, report)
                if (!result) {
                    Log.w(TAG, "Failed to send mouse report to ${connected.device.address}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                return false
            }
        }
        return true
    }

    fun pressAndReleaseKey(modifier: Int, keyCode: Int) {
        val pressReport = HidReportBuilder.buildKeyboardReport(modifier, listOf(keyCode))
        val releaseReport = HidReportBuilder.buildKeyboardReleaseReport()

        sendKeyboardReport(pressReport)
        Thread.sleep(20)
        sendKeyboardReport(releaseReport)
    }

    fun typeText(text: String) {
        text.forEach { char ->
            val (modifier, keyCode) = charToKeyCode(char)
            if (keyCode != HidReportBuilder.KeyCode.NONE) {
                sendKeyboardReport(HidReportBuilder.buildKeyboardReport(modifier, listOf(keyCode)))
                Thread.sleep(10)
                sendKeyboardReport(HidReportBuilder.buildKeyboardReleaseReport())
                Thread.sleep(10)
            }
        }
    }

    fun mouseMove(deltaX: Byte, deltaY: Byte) {
        val report = HidReportBuilder.buildMouseReport(0, deltaX, deltaY, 0)
        sendMouseReport(report)
    }

    /**
     * 鼠标拖拽：按住指定按键的同时移动（用于触摸板双指/双击拖拽）
     */
    fun mouseDrag(button: Int, deltaX: Byte, deltaY: Byte) {
        val report = HidReportBuilder.buildMouseReport(button, deltaX, deltaY, 0)
        sendMouseReport(report)
    }

    fun mouseClick(button: Int) {
        val pressReport = HidReportBuilder.buildMouseReport(button, 0, 0, 0)
        val releaseReport = HidReportBuilder.buildMouseReport(0, 0, 0, 0)

        sendMouseReport(pressReport)
        Thread.sleep(20)
        sendMouseReport(releaseReport)
    }

    fun mouseScroll(wheelDelta: Byte) {
        val report = HidReportBuilder.buildMouseReport(0, 0, 0, wheelDelta)
        sendMouseReport(report)
        Thread.sleep(10)
        sendMouseReport(HidReportBuilder.buildMouseReport(0, 0, 0, 0))
    }

    fun mousePress(button: Int) {
        val pressReport = HidReportBuilder.buildMouseReport(button, 0, 0, 0)
        sendMouseReport(pressReport)
    }

    /**
     * 鼠标按键按住一段时间后释放
     * 蓝牙HID需要持续发送报告才能保持按键状态
     */
    fun mousePressAndHold(button: Int, holdMs: Long) {
        val pressReport = HidReportBuilder.buildMouseReport(button, 0, 0, 0)
        val releaseReport = HidReportBuilder.buildMouseReport(0, 0, 0, 0)

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < holdMs) {
            sendMouseReport(pressReport)
            Thread.sleep(15) // ~60Hz 发送频率
        }
        sendMouseReport(releaseReport)
    }

    fun mouseRelease() {
        val releaseReport = HidReportBuilder.buildMouseReport(0, 0, 0, 0)
        sendMouseReport(releaseReport)
    }

    private fun charToKeyCode(char: Char): Pair<Int, Int> {
        return when (char) {
            in 'a'..'z' -> Pair(0, HidReportBuilder.KeyCode.A + (char - 'a'))
            in 'A'..'Z' -> Pair(HidReportBuilder.KeyboardModifier.LEFT_SHIFT,
                HidReportBuilder.KeyCode.A + (char - 'A'))
            in '0'..'9' -> Pair(0, HidReportBuilder.KeyCode.NUM_1 + (char - '1'))
            ' ' -> Pair(0, HidReportBuilder.KeyCode.SPACE)
            '\n' -> Pair(0, HidReportBuilder.KeyCode.ENTER)
            '\t' -> Pair(0, HidReportBuilder.KeyCode.TAB)
            '\b' -> Pair(0, HidReportBuilder.KeyCode.BACKSPACE)
            '-' -> Pair(0, HidReportBuilder.KeyCode.MINUS)
            '=' -> Pair(0, HidReportBuilder.KeyCode.EQUAL)
            '[' -> Pair(0, HidReportBuilder.KeyCode.LEFT_BRACKET)
            ']' -> Pair(0, HidReportBuilder.KeyCode.RIGHT_BRACKET)
            '\\' -> Pair(0, HidReportBuilder.KeyCode.BACKSLASH)
            ';' -> Pair(0, HidReportBuilder.KeyCode.SEMICOLON)
            '\'' -> Pair(0, HidReportBuilder.KeyCode.APOSTROPHE)
            ',' -> Pair(0, HidReportBuilder.KeyCode.COMMA)
            '.' -> Pair(0, HidReportBuilder.KeyCode.PERIOD)
            '/' -> Pair(0, HidReportBuilder.KeyCode.SLASH)
            '`' -> Pair(0, HidReportBuilder.KeyCode.GRAVE)
            else -> Pair(0, HidReportBuilder.KeyCode.NONE)
        }
    }

    private fun addConnectedDevice(device: BluetoothDevice) {
        val current = _connectedDevices.value.toMutableList()
        if (current.none { it.device.address == device.address }) {
            current.add(ConnectedDevice(device, DeviceType.COMPOSITE))
            _connectedDevices.value = current
        }
    }

    private fun onDeviceDisconnected(device: BluetoothDevice) {
        registeredDevices.remove(device)
        _connectedDevices.value = _connectedDevices.value.filter {
            it.device.address != device.address
        }
        if (_connectedDevices.value.isEmpty()) {
            _hidState.value = HidState.DISCONNECTED
            stopHeartbeat()
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                renewWakeLock()
                if (_hidState.value == HidState.CONNECTED) {
                    _connectedDevices.value.forEach { connected ->
                        try {
                            val report = HidReportBuilder.buildKeyboardReleaseReport()
                            bluetoothHidDevice?.sendReport(connected.device, 0, report)
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Heartbeat send failed: ${e.message}")
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Heartbeat started")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect(device: BluetoothDevice) {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_hidState.value != HidState.CONNECTED && hidAppRegistered) {
                Log.d(TAG, "Attempting reconnect to ${device.address}")
                if (!connectDevice(device)) {
                    Log.w(TAG, "Reconnect failed, restarting advertising for host to find us")
                    startAdvertising()
                }
            }
        }
    }

    override fun onDestroy() {
        instance = null
        stopHeartbeat()
        reconnectJob?.cancel()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        // restore original name
        originalBluetoothName?.let { original ->
            try {
                bluetoothAdapter?.setName(original)
            } catch (e: Exception) {
                // ignore
            }
        }
        instance = null

        // 销毁宏通知
        dismissMacroNotification()
        try {
            unregisterReceiver(macroNotificationReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering macro receiver: ${e.message}")
        }

        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        }

        stopAdvertising()

        _connectedDevices.value.forEach {
            try {
                bluetoothHidDevice?.disconnect(it.device)
            } catch (e: SecurityException) {
                Log.w(TAG, "Error disconnecting: ${e.message}")
            }
        }

        try {
            bluetoothHidDevice?.unregisterApp()
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering HID app: ${e.message}")
        }

        super.onDestroy()
    }
}