package com.example.rockmacro.macro

import android.util.Log
import com.example.rockmacro.hid.BluetoothHidService
import com.example.rockmacro.hid.HidReportBuilder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MacroEngine(
    private var hidService: BluetoothHidService?,
    private val repository: MacroRepository? = null
) {
    companion object {
        private const val TAG = "MacroEngine"
    }

    enum class MacroState {
        IDLE,
        RECORDING,
        PLAYING,
        PAUSED,
        STOPPED
    }

    private val _state = MutableStateFlow(MacroState.IDLE)
    val state: StateFlow<MacroState> = _state

    private val _currentMacro = MutableStateFlow<Macro?>(null)
    val currentMacro: StateFlow<Macro?> = _currentMacro

    private val _recordedActions = MutableStateFlow<List<MacroAction>>(emptyList())
    val recordedActions: StateFlow<List<MacroAction>> = _recordedActions

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _playProgress = MutableStateFlow(0f)
    val playProgress: StateFlow<Float> = _playProgress

    private val _currentRepeatCount = MutableStateFlow(0)
    val currentRepeatCount: StateFlow<Int> = _currentRepeatCount

    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var isRecording = false

    init {
        repository?.let { repo ->
            val saved = repo.loadMacros()
            if (saved.isNotEmpty()) {
                _macros.value = saved
                Log.d(TAG, "Restored ${saved.size} macros from storage")
            } else {
                // 添加内置宏：丢球（无限循环）
                val builtInMacro = createThrowBallMacro()
                _macros.value = listOf(builtInMacro)
                repo.saveMacros(_macros.value)
                Log.d(TAG, "Added built-in macro: ${builtInMacro.name}")
            }
        }
    }

    private fun createThrowBallMacro(): Macro {
        return Macro(
            id = Long.MAX_VALUE,
            name = "丢球",
            notes = "左键按下保持200ms→释放→等150ms→右键按下保持50ms→释放→等50ms，无限循环",
            actions = listOf(
                MacroAction.MousePress(HidReportBuilder.MouseButton.LEFT, holdMs = 200),
                MacroAction.Delay(150),
                MacroAction.MousePress(HidReportBuilder.MouseButton.RIGHT, holdMs = 50),
                MacroAction.Delay(50)
            ),
            repeatCount = 1,
            infiniteRepeat = true
        )
    }

    private fun persistMacros() {
        repository?.saveMacros(_macros.value)
    }

    fun startRecording() {
        _state.value = MacroState.RECORDING
        _recordedActions.value = emptyList()
        isRecording = true
    }

    fun stopRecording() {
        isRecording = false
        _state.value = MacroState.IDLE
    }

    fun addRecordedAction(action: MacroAction) {
        _recordedActions.value = _recordedActions.value + action
    }

    fun removeRecordedAction(action: MacroAction) {
        _recordedActions.value = _recordedActions.value.filter { it !== action }
    }

    fun clearRecordedActions() {
        _recordedActions.value = emptyList()
    }

    fun saveMacro(name: String, notes: String = ""): Macro {
        val macro = Macro(
            name = name,
            notes = notes,
            actions = _recordedActions.value.toList()
        )
        _macros.value = _macros.value + macro
        _currentMacro.value = macro
        _recordedActions.value = emptyList()
        persistMacros()
        return macro
    }

    fun deleteMacro(macro: Macro) {
        _macros.value = _macros.value.filter { it.id != macro.id }
        if (_currentMacro.value?.id == macro.id) {
            _currentMacro.value = null
        }
        persistMacros()
    }

    fun selectMacro(macro: Macro) {
        _currentMacro.value = macro
    }

    fun updateMacroName(macro: Macro, newName: String) {
        val updated = macro.copy(name = newName)
        _macros.value = _macros.value.map { if (it.id == macro.id) updated else it }
        if (_currentMacro.value?.id == macro.id) {
            _currentMacro.value = updated
        }
        persistMacros()
    }

    fun updateMacroNotes(macro: Macro, notes: String) {
        val updated = macro.copy(notes = notes)
        _macros.value = _macros.value.map { if (it.id == macro.id) updated else it }
        if (_currentMacro.value?.id == macro.id) {
            _currentMacro.value = updated
        }
        persistMacros()
    }

    fun updateMacroActions(macro: Macro, actions: List<MacroAction>) {
        val updated = macro.copy(actions = actions)
        _macros.value = _macros.value.map { if (it.id == macro.id) updated else it }
        if (_currentMacro.value?.id == macro.id) {
            _currentMacro.value = updated
        }
        persistMacros()
    }

    fun updateMacroSettings(macro: Macro, repeatCount: Int, infiniteRepeat: Boolean) {
        val updated = macro.copy(repeatCount = repeatCount, infiniteRepeat = infiniteRepeat)
        _macros.value = _macros.value.map { if (it.id == macro.id) updated else it }
        if (_currentMacro.value?.id == macro.id) {
            _currentMacro.value = updated
        }
        persistMacros()
    }

    fun addMacro(macro: Macro) {
        _macros.value = _macros.value + macro
        _currentMacro.value = macro
        persistMacros()
    }

    fun saveNewMacro(
        name: String,
        notes: String = "",
        actions: List<MacroAction> = emptyList(),
        repeatCount: Int = 1,
        infiniteRepeat: Boolean = false
    ): Macro {
        val macro = Macro(
            name = name,
            notes = notes,
            actions = actions,
            repeatCount = repeatCount,
            infiniteRepeat = infiniteRepeat
        )
        _macros.value = _macros.value + macro
        _currentMacro.value = macro
        persistMacros()
        return macro
    }

    fun updateMacro(
        targetId: Long,
        actions: List<MacroAction>? = null,
        notes: String? = null,
        name: String? = null,
        repeatCount: Int? = null,
        infiniteRepeat: Boolean? = null
    ) {
        _macros.value = _macros.value.map {
            if (it.id == targetId) it.copy(
                actions = actions ?: it.actions,
                notes = notes ?: it.notes,
                name = name ?: it.name,
                repeatCount = repeatCount ?: it.repeatCount,
                infiniteRepeat = infiniteRepeat ?: it.infiniteRepeat
            ) else it
        }
        _macros.value.find { it.id == targetId }?.let { updated ->
            if (_currentMacro.value?.id == targetId) {
                _currentMacro.value = updated
            }
        }
        persistMacros()
    }

    // 执行宏
    fun playMacro(macro: Macro) {
        _currentMacro.value = macro
        _currentRepeatCount.value = 0
        _state.value = MacroState.PLAYING
        playJob?.cancel()
        playJob = scope.launch {
            try {
                val totalActions = countTotalActions(macro.actions)
                var executedActions = 0

                val maxRepeat = if (macro.infiniteRepeat) Int.MAX_VALUE else macro.repeatCount
                for (i in 0 until maxRepeat) {
                    if (_state.value != MacroState.PLAYING) break
                    _currentRepeatCount.value = i + 1
                    executeActions(macro.actions, totalActions, executedActions)
                    executedActions = (executedActions + totalActions).toInt()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing macro: ${e.message}")
            } finally {
                if (_state.value == MacroState.PLAYING) {
                    _state.value = MacroState.IDLE
                }
                _playProgress.value = 0f
                _currentRepeatCount.value = 0
            }
        }
    }

    fun playCurrentMacro() {
        _currentMacro.value?.let { playMacro(it) }
    }

    fun stopPlayback() {
        playJob?.cancel()
        _state.value = MacroState.IDLE
        _playProgress.value = 0f
        _currentRepeatCount.value = 0
    }

    fun pausePlayback() {
        _state.value = MacroState.PAUSED
    }

    fun resumePlayback() {
        if (playJob?.isActive != true && _state.value == MacroState.PAUSED) {
            // 协程已结束（在两次循环间隙暂停导致），重新启动
            _currentMacro.value?.let { playMacro(it) }
        } else {
            _state.value = MacroState.PLAYING
        }
    }

    private fun countTotalActions(actions: List<MacroAction>): Long {
        var count = 0L
        for (action in actions) {
            count++
            when {
                action is MacroAction.Repeat -> {
                    count += countTotalActions(action.actions) * action.times
                }
                action is MacroAction.Loop -> {
                    if (!action.isInfinite) {
                        count += countTotalActions(action.actions) * action.times
                    }
                }
            }
        }
        return count
    }

    private suspend fun executeActions(actions: List<MacroAction>, totalActions: Long, executedOffset: Int) {
        var executed = executedOffset
        for (action in actions) {
            if (_state.value != MacroState.PLAYING) {
                while (_state.value == MacroState.PAUSED) {
                    delay(100)
                }
                if (_state.value != MacroState.PLAYING) break
            }

            when (action) {
                is MacroAction.KeyPress -> {
                    val report = HidReportBuilder.buildKeyboardReport(
                        action.modifier, listOf(action.keyCode)
                    )
                    hidService?.sendKeyboardReport(report)
                    delay(action.delayMs)
                }
                is MacroAction.KeyRelease -> {
                    hidService?.sendKeyboardReport(
                        HidReportBuilder.buildKeyboardReleaseReport()
                    )
                    delay(action.delayMs)
                }
                is MacroAction.KeyTap -> {
                    hidService?.pressAndReleaseKey(action.modifier, action.keyCode)
                    delay(action.delayMs)
                }
                is MacroAction.TypeText -> {
                    hidService?.typeText(action.text)
                }
                is MacroAction.MouseMove -> {
                    hidService?.mouseMove(action.deltaX, action.deltaY)
                }
                is MacroAction.MousePress -> {
                    if (action.holdMs > 0) {
                        hidService?.mousePressAndHold(action.button, action.holdMs)
                    } else {
                        hidService?.mousePress(action.button)
                    }
                }
                is MacroAction.MouseRelease -> {
                    hidService?.mouseRelease()
                }
                is MacroAction.MouseClick -> {
                    hidService?.mouseClick(action.button)
                    delay(action.delayMs)
                }
                is MacroAction.MouseScroll -> {
                    hidService?.mouseScroll(action.delta)
                }
                is MacroAction.MouseMoveAbs -> {
                    val steps = 10
                    val stepX = (action.x / steps).toByte()
                    val stepY = (action.y / steps).toByte()
                    for (i in 0 until steps) {
                        hidService?.mouseMove(stepX, stepY)
                        delay(5)
                    }
                    val remX = (action.x % steps).toByte()
                    val remY = (action.y % steps).toByte()
                    if (remX != 0.toByte() || remY != 0.toByte()) {
                        hidService?.mouseMove(remX, remY)
                    }
                }
                is MacroAction.Delay -> {
                    delay(action.milliseconds)
                }
                is MacroAction.Repeat -> {
                    val subTotal = countTotalActions(action.actions)
                    for (i in 0 until action.times) {
                        if (_state.value != MacroState.PLAYING) break
                        executeActions(action.actions, subTotal, 0)
                    }
                }
                is MacroAction.Loop -> {
                    val subTotal = countTotalActions(action.actions)
                    val maxLoop = if (action.isInfinite) Int.MAX_VALUE else action.times
                    for (i in 0 until maxLoop) {
                        if (_state.value != MacroState.PLAYING) break
                        executeActions(action.actions, subTotal, 0)
                    }
                }
            }
            executed++
            if (totalActions > 0) {
                _playProgress.value = executed.toFloat() / totalActions.toFloat()
            }
        }
    }

    /** 销毁引擎，取消所有协程 */
    fun destroy() {
        stopPlayback()
        scope.cancel()
    }

    /** 更新 HID 服务引用（Activity 重建后重新绑定） */
    fun updateHidService(service: BluetoothHidService?) {
        hidService = service
    }
}