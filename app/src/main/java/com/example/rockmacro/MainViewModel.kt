package com.example.rockmacro

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rockmacro.hid.BluetoothHidService
import com.example.rockmacro.hid.HidReportBuilder
import com.example.rockmacro.macro.Macro
import com.example.rockmacro.macro.MacroAction
import com.example.rockmacro.macro.MacroEngine
import com.example.rockmacro.macro.MacroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    data class ScannedDevice(
        val device: BluetoothDevice,
        val name: String,
        val address: String
    )

    enum class ConnectionState {
        IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, ADVERTISING, ERROR
    }

    // 蓝牙相关状态
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<ScannedDevice>> = _pairedDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // HID服务就绪状态
    private val _hidReady = MutableStateFlow(false)
    val hidReady: StateFlow<Boolean> = _hidReady.asStateFlow()

    // 宏相关状态
    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _selectedMacro = MutableStateFlow<Macro?>(null)
    val selectedMacro: StateFlow<Macro?> = _selectedMacro.asStateFlow()

    private val _macroEngineState = MutableStateFlow(MacroEngine.MacroState.IDLE)
    val macroEngineState: StateFlow<MacroEngine.MacroState> = _macroEngineState.asStateFlow()

    private val _recordedActions = MutableStateFlow<List<MacroAction>>(emptyList())
    val recordedActions: StateFlow<List<MacroAction>> = _recordedActions.asStateFlow()

    private val _playProgress = MutableStateFlow(0f)
    val playProgress: StateFlow<Float> = _playProgress.asStateFlow()

    // 控制面板状态
    private val _isKeyboardMode = MutableStateFlow(true)
    val isKeyboardMode: StateFlow<Boolean> = _isKeyboardMode.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
// 核心服务
    var hidService: BluetoothHidService? = null
    private var macroEngine: MacroEngine? = null

    // 宏通知设置
    private val prefs: SharedPreferences = application.getSharedPreferences("macro_settings", Context.MODE_PRIVATE)
    private val _macroNotificationEnabled = MutableStateFlow(prefs.getBoolean("notification_enabled", true))
    val macroNotificationEnabled: StateFlow<Boolean> = _macroNotificationEnabled.asStateFlow()

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                    device?.let { addScannedDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _connectionState.value = ConnectionState.IDLE
                }
            }
        }
    }

    init {
        loadPairedDevices()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                application.registerReceiver(scanReceiver, filter)
            }
            Log.d(TAG, "扫描广播接收器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "注册扫描广播接收器失败: ${e.message}")
            _errorMessage.value = "蓝牙广播接收器注册失败"
        }
    }

    fun bindHidService(service: BluetoothHidService?) {
        hidService = service
        macroEngine = MacroEngine(service, MacroRepository(getApplication()))

        viewModelScope.launch {
            service?.hidState?.collect { state ->
                when (state) {
                    BluetoothHidService.HidState.CONNECTED -> {
                        _connectionState.value = ConnectionState.CONNECTED
                        _hidReady.value = true
                    }
                    BluetoothHidService.HidState.CONNECTING -> {
                        _connectionState.value = ConnectionState.CONNECTING
                    }
                    BluetoothHidService.HidState.DISCONNECTED -> {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _hidReady.value = true
                    }
                    BluetoothHidService.HidState.ADVERTISING -> {
                        _connectionState.value = ConnectionState.ADVERTISING
                        _hidReady.value = true
                    }
                    BluetoothHidService.HidState.ERROR -> {
                        _connectionState.value = ConnectionState.ERROR
                        _errorMessage.value = "蓝牙HID服务初始化失败"
                        _hidReady.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            service?.connectedDevices?.collect { devices ->
                _connectedDeviceName.value = if (devices.isNotEmpty()) {
                    devices.first().device.name ?: devices.first().device.address
                } else null
            }
        }

        // 设置宏通知回调（来自通知栏的暂停/停止/继续操作）
        service?.setMacroControlCallback { action ->
            when (action) {
                "pause" -> pauseMacroPlayback()
                "stop" -> stopMacroPlayback()
                "resume" -> resumeMacroPlayback()
            }
        }

        macroEngine?.let { engine ->
            viewModelScope.launch {
                engine.state.collect { state ->
                    _macroEngineState.value = state
                    updateMacroNotification(state)
                }
            }
            viewModelScope.launch { engine.macros.collect { _macros.value = it } }
            viewModelScope.launch { engine.currentMacro.collect { _selectedMacro.value = it } }
            viewModelScope.launch { engine.playProgress.collect { _playProgress.value = it } }
            viewModelScope.launch { engine.recordedActions.collect { _recordedActions.value = it } }
        }
    }

    // ==================== HID服务 ====================

    fun initHidService() {
        // 初始化HID服务
        if (hidService != null) {
            _hidReady.value = true
        } else {
            _connectionState.value = ConnectionState.ADVERTISING
        }
    }

    // ==================== 蓝牙操作 ====================

    fun startScan() {
        Log.d(TAG, "开始扫描设备...")
        
        // 先检查蓝牙是否开启
        if (bluetoothAdapter?.isEnabled != true) {
            _errorMessage.value = "蓝牙未开启，请先打开蓝牙"
            _connectionState.value = ConnectionState.IDLE
            return
        }

        // 检查权限
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = getApplication<Application>()
                val hasScanPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.BLUETOOTH_SCAN
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasScanPermission) {
                    _errorMessage.value = "缺少蓝牙扫描权限"
                    _connectionState.value = ConnectionState.IDLE
                    return
                }
            }
        } catch (_: Exception) {}

        // 取消之前的扫描
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (_: SecurityException) {}

        _scannedDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING
        
        val started = try {
            bluetoothAdapter?.startDiscovery() ?: false
        } catch (e: SecurityException) {
            _errorMessage.value = "缺少蓝牙扫描权限"
            _connectionState.value = ConnectionState.IDLE
            false
        }

        if (!started) {
            Log.w(TAG, "startDiscovery() 返回 false，启动扫描失败")
            // 不立即设置错误，因为可能仍在扫描中
        } else {
            Log.d(TAG, "扫描已启动")
        }
    }

    fun stopScan() {
        try { bluetoothAdapter?.cancelDiscovery() } catch (_: SecurityException) {}
        _connectionState.value = ConnectionState.IDLE
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (hidService == null) {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "蓝牙HID服务未就绪，请稍后再试"
            return
        }
        try { bluetoothAdapter?.cancelDiscovery() } catch (_: SecurityException) {}
        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        val success = hidService!!.connectDevice(device)
        if (!success) {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "连接失败，请确保已启动广播且目标设备支持蓝牙HID"
        }
    }

    fun disconnect() {
        hidService?.let { service ->
            service.connectedDevices.value.forEach { dev -> service.disconnectDevice(dev.device) }
        }
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
    }

    // ==================== 键盘操作 ====================

    fun typeText(text: String) {
        hidService?.typeText(text)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.TypeText(text))
        }
    }

    fun pressKey(modifier: Int, keyCode: Int) {
        val report = HidReportBuilder.buildKeyboardReport(modifier, listOf(keyCode))
        hidService?.sendKeyboardReport(report)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.KeyPress(modifier, keyCode))
        }
    }

    fun releaseAllKeys() {
        hidService?.sendKeyboardReport(HidReportBuilder.buildKeyboardReleaseReport())
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.KeyRelease())
        }
    }

    fun pressAndReleaseKey(modifier: Int, keyCode: Int) {
        hidService?.pressAndReleaseKey(modifier, keyCode)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.KeyTap(modifier, keyCode))
        }
    }

    // ==================== 鼠标操作 ====================

    fun mouseMove(deltaX: Byte, deltaY: Byte) {
        hidService?.mouseMove(deltaX, deltaY)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.MouseMove(deltaX, deltaY))
        }
    }

    fun mouseDrag(button: Int, deltaX: Byte, deltaY: Byte) {
        hidService?.mouseDrag(button, deltaX, deltaY)
    }

    fun mouseClick(button: Int) {
        hidService?.mouseClick(button)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.MouseClick(button))
        }
    }

    fun mousePress(button: Int) {
        hidService?.mousePress(button)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.MousePress(button))
        }
    }

    fun mouseRelease() {
        hidService?.mouseRelease()
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.MouseRelease())
        }
    }

    fun mouseScroll(wheelDelta: Byte) {
        hidService?.mouseScroll(wheelDelta)
        if (macroEngine?.state?.value == MacroEngine.MacroState.RECORDING) {
            macroEngine?.addRecordedAction(MacroAction.MouseScroll(wheelDelta))
        }
    }

    // ==================== 宏操作 ====================

    fun startMacroRecording() { macroEngine?.startRecording() }
    fun stopMacroRecording() { macroEngine?.stopRecording() }
    fun saveMacro(name: String, notes: String = "") { macroEngine?.saveMacro(name, notes) }
    fun deleteMacro(macro: Macro) { macroEngine?.deleteMacro(macro) }
    fun selectMacro(macro: Macro) { macroEngine?.selectMacro(macro) }
    fun playMacro(macro: Macro) { macroEngine?.playMacro(macro) }
    fun playSelectedMacro() { macroEngine?.playCurrentMacro() }
    fun stopMacroPlayback() { 
        macroEngine?.stopPlayback()
        hidService?.dismissMacroNotification()
    }
    fun pauseMacroPlayback() { macroEngine?.pausePlayback() }
    fun resumeMacroPlayback() { macroEngine?.resumePlayback() }
    fun addActionToRecording(action: MacroAction) { macroEngine?.addRecordedAction(action) }
    fun removeActionFromRecording(action: MacroAction) { macroEngine?.removeRecordedAction(action) }
    fun clearRecordedActions() { macroEngine?.clearRecordedActions() }
    fun updateMacroName(macro: Macro, newName: String) { macroEngine?.updateMacroName(macro, newName) }
    fun updateMacroNotes(macro: Macro, notes: String) { macroEngine?.updateMacroNotes(macro, notes) }
    fun updateMacroActions(macro: Macro, actions: List<MacroAction>) { macroEngine?.updateMacroActions(macro, actions) }
    fun updateMacroSettings(macro: Macro, repeatCount: Int, infiniteRepeat: Boolean) {
        macroEngine?.updateMacroSettings(macro, repeatCount, infiniteRepeat)
    }
    fun addMacro(macro: Macro) { macroEngine?.addMacro(macro) }
    fun saveNewMacro(name: String, notes: String = "", actions: List<MacroAction> = emptyList(),
                     repeatCount: Int = 1, infiniteRepeat: Boolean = false): Macro? {
        return macroEngine?.saveNewMacro(name, notes, actions, repeatCount, infiniteRepeat)
    }
    fun updateMacroAll(targetId: Long, actions: List<MacroAction>? = null, notes: String? = null,
                       name: String? = null, repeatCount: Int? = null, infiniteRepeat: Boolean? = null) {
        macroEngine?.updateMacro(targetId, actions, notes, name, repeatCount, infiniteRepeat)
    }

    // ==================== 蓝牙广播 ====================
fun makeDiscoverable() {
        _connectionState.value = ConnectionState.ADVERTISING
        hidService?.makeDiscoverable() 
    }
    fun stopAdvertising() { 
        hidService?.stopAdvertising()
        _connectionState.value = ConnectionState.IDLE
    }

    fun createDiscoverableIntent(): android.content.Intent? {
        return try {
            android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating discoverable intent: ${e.message}")
            null
        }
    }

    // ==================== UI状态 ====================

    fun setKeyboardMode(isKeyboard: Boolean) { _isKeyboardMode.value = isKeyboard }
    fun setInputText(text: String) { _inputText.value = text }
    fun clearError() { _errorMessage.value = null }
    fun setErrorMessage(msg: String) { _errorMessage.value = msg }

    fun setMacroNotificationEnabled(enabled: Boolean) {
        _macroNotificationEnabled.value = enabled
        prefs.edit().putBoolean("notification_enabled", enabled).apply()
        if (!enabled) {
            hidService?.dismissMacroNotification()
        }
    }

    private fun addScannedDevice(device: BluetoothDevice) {
        val name = device.name ?: "未知设备"
        if (name.isNotEmpty()) {
            val scanned = ScannedDevice(device, name, device.address)
            val current = _scannedDevices.value.toMutableList()
            if (current.none { it.address == device.address }) {
                current.add(scanned)
                _scannedDevices.value = current
            }
        }
    }

    fun loadPairedDevices() {
        val devices = try {
            bluetoothAdapter?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) { emptySet() }
        _pairedDevices.value = devices.map { dev ->
            ScannedDevice(dev, dev.name ?: "未知设备", dev.address)
        }
    }

    private fun updateMacroNotification(state: MacroEngine.MacroState) {
        if (!_macroNotificationEnabled.value) return
        val service = hidService ?: return
        when (state) {
            MacroEngine.MacroState.PLAYING -> {
                val name = macroEngine?.currentMacro?.value?.name ?: "宏"
                service.showMacroNotification("运行中", name)
            }
            MacroEngine.MacroState.PAUSED -> {
                val name = macroEngine?.currentMacro?.value?.name ?: "宏"
                service.showMacroNotification("已暂停", name)
            }
            else -> service.dismissMacroNotification()
        }
    }

    override fun onCleared() {
        super.onCleared()
        
        // 应用退出前保存宏
        macroEngine?.let { engine ->
            try {
                val currentMacros = engine.macros.value
                if (currentMacros.isNotEmpty()) {
                    MacroRepository(getApplication()).saveMacros(currentMacros)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving macros on exit: ${e.message}")
            }
        }
        
        try { getApplication<Application>().unregisterReceiver(scanReceiver) }
        catch (e: Exception) { Log.w(TAG, "Error: ${e.message}") }
    }
}