package com.example.rockmacro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.rockmacro.MainViewModel
import com.example.rockmacro.hid.HidReportBuilder
import com.example.rockmacro.macro.MacroAction
import com.example.rockmacro.macro.MacroEngine

enum class DialogMode { CREATE, EDIT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val macros by viewModel.macros.collectAsState()
    val macroState by viewModel.macroEngineState.collectAsState()
    val recordingActions by viewModel.recordedActions.collectAsState()
    val notificationEnabled by viewModel.macroNotificationEnabled.collectAsState()
    val superIslandEnabled by viewModel.superIslandEnabled.collectAsState()
    val currentRepeatCount by viewModel.currentRepeatCount.collectAsState()
    
    var selectedMacroIndex by remember { mutableStateOf(-1) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // 统一宏编辑对话框状态
    var showMacroDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf(DialogMode.CREATE) }
    var dialogName by remember { mutableStateOf("") }
    var dialogNotes by remember { mutableStateOf("") }
    val dialogActions = remember { mutableStateListOf<MacroAction>() }
    var dialogLoopCount by remember { mutableStateOf(1) }
    var dialogInfiniteRepeat by remember { mutableStateOf(false) }
    
    // 对话框内子对话框状态
    var dialogShowKey by remember { mutableStateOf(false) }
    var dialogKeyCode by remember { mutableStateOf("") }
    var dialogShowDelay by remember { mutableStateOf(false) }
    var dialogDelayMs by remember { mutableStateOf("") }
    var dialogShowMouse by remember { mutableStateOf(false) }
    var dialogMouseHoldMs by remember { mutableStateOf("") }
    var dialogMouseMode by remember { mutableStateOf("click") } // "click" 或 "press"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部：状态 + 控制栏（固定）
        
        // 录制状态卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (macroState == MacroEngine.MacroState.RECORDING)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "状态: ${
                        when (macroState) {
                            MacroEngine.MacroState.IDLE -> "就绪"
                            MacroEngine.MacroState.RECORDING -> "正在录制..."
                            MacroEngine.MacroState.PLAYING -> "正在播放"
                            MacroEngine.MacroState.PAUSED -> "已暂停"
                            else -> "未知"
                        }
                    }",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                // 显示当前执行进度
                if (macroState == MacroEngine.MacroState.PLAYING || macroState == MacroEngine.MacroState.PAUSED) {
                    val selectedMacro = if (selectedMacroIndex in macros.indices) macros[selectedMacroIndex] else null
                    if (selectedMacro != null) {
                        val total = if (selectedMacro.infiniteRepeat) "∞" else selectedMacro.repeatCount.toString()
                        Text(
                            text = "执行: 第 ${currentRepeatCount}/${total} 次",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                if (recordingActions.isNotEmpty()) {
                    Text(
                        text = "待添加动作: ${recordingActions.size} 个",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    // 显示待添加动作列表
                    recordingActions.take(3).forEachIndexed { i, act ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "  ${i+1}. ${actionSummary(act)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.removeActionFromRecording(act) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("x", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            }
                        }
                    }
                    if (recordingActions.size > 3) {
                        Text(
                            text = "  +${recordingActions.size-3} 个更多",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (recordingActions.isEmpty()) {
                    Text(
                        text = "点击下方按钮添加动作，或按键面板中录制",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 通知开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "实况通知",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = notificationEnabled,
                onCheckedChange = { viewModel.setMacroNotificationEnabled(it) }
            )
        }

        // 超级岛开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "小米超级岛",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = superIslandEnabled,
                onCheckedChange = { viewModel.setSuperIslandEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 控制栏：录制 + 新建宏（点击后弹窗统一管理所有设置）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (macroState == MacroEngine.MacroState.IDLE) {
                        viewModel.startMacroRecording()
                    } else if (macroState == MacroEngine.MacroState.RECORDING) {
                        viewModel.stopMacroRecording()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = if (macroState == MacroEngine.MacroState.RECORDING) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (macroState == MacroEngine.MacroState.RECORDING) "停止录制" else "录制")
            }

            if (recordingActions.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.clearRecordedActions() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空 (${recordingActions.size})")
                }
            }

            Button(
                onClick = {
                    // 统一对话框：新建宏
                    dialogName = ""
                    dialogNotes = ""
                    dialogActions.clear()
                    dialogActions.addAll(recordingActions)
                    dialogInfiniteRepeat = false
                    dialogLoopCount = 1
                    dialogMode = DialogMode.CREATE
                    showMacroDialog = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("新建宏 (${recordingActions.size})")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 宏列表（可滚动区域）
        Text(
            text = "宏列表",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(macros) { index, macro ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            selectedMacroIndex = index
                        },
                    colors = if (selectedMacroIndex == index) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = macro.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "循环: ${if (macro.infiniteRepeat) "无限" else macro.repeatCount}次, ${macro.actions.size}个动作",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row {
                                IconButton(onClick = { 
                                    selectedMacroIndex = index
                                    // 统一编辑对话框
                                    dialogName = macro.name
                                    dialogNotes = macro.notes
                                    dialogActions.clear()
                                    dialogActions.addAll(macro.actions)
                                    dialogLoopCount = macro.repeatCount
                                    dialogInfiniteRepeat = macro.infiniteRepeat
                                    dialogMode = DialogMode.EDIT
                                    showMacroDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "编辑"
                                    )
                                }
                                
                                IconButton(onClick = { 
                                    selectedMacroIndex = index
                                    showDeleteConfirmation = true 
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "删除"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 显示前几个动作
                        Column {
                            macro.actions.take(3).forEach { action ->
                                Text(
                                    text = actionSummary(action),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (macro.actions.size > 3) {
                            Text(
                                text = "+ ${macro.actions.size - 3} 个更多动作",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 底部固定播放控制栏（始终显示，不突然弹出）
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停/继续按钮（合并）
                Button(
                    onClick = {
                        when (macroState) {
                            MacroEngine.MacroState.IDLE -> {
                                if (selectedMacroIndex >= 0 && selectedMacroIndex < macros.size) {
                                    viewModel.playMacro(macros[selectedMacroIndex])
                                }
                            }
                            MacroEngine.MacroState.PLAYING -> viewModel.pauseMacroPlayback()
                            MacroEngine.MacroState.PAUSED -> viewModel.resumeMacroPlayback()
                            else -> {}
                        }
                    },
                    enabled = (macroState == MacroEngine.MacroState.IDLE && selectedMacroIndex >= 0) ||
                              macroState == MacroEngine.MacroState.PLAYING ||
                              macroState == MacroEngine.MacroState.PAUSED,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when (macroState) {
                            MacroEngine.MacroState.IDLE -> "播放宏"
                            MacroEngine.MacroState.PLAYING -> "暂停"
                            MacroEngine.MacroState.PAUSED -> "继续"
                            else -> "播放宏"
                        }
                    )
                }

                // 停止按钮
                Button(
                    onClick = { viewModel.stopMacroPlayback() },
                    enabled = macroState == MacroEngine.MacroState.PLAYING || 
                              macroState == MacroEngine.MacroState.PAUSED,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("停止", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }

    // ============ 统一宏编辑对话框（新建/编辑）============
    if (showMacroDialog) {
        var expandedActions by remember { mutableStateOf(false) }
        val title = if (dialogMode == DialogMode.CREATE) "新建宏" else "编辑宏"
        AlertDialog(
            onDismissRequest = { showMacroDialog = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    // 宏名称
                    OutlinedTextField(
                        value = dialogName,
                        onValueChange = { dialogName = it },
                        label = { Text("宏名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 备注
                    OutlinedTextField(
                        value = dialogNotes,
                        onValueChange = { dialogNotes = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 循环次数 + 无限循环
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = dialogLoopCount.toString(),
                            onValueChange = {
                                val num = it.toIntOrNull()
                                if (num != null && num > 0) {
                                    dialogLoopCount = num
                                }
                            },
                            label = { Text("循环次数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            enabled = !dialogInfiniteRepeat,
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        FilterChip(
                            selected = dialogInfiniteRepeat,
                            onClick = { dialogInfiniteRepeat = !dialogInfiniteRepeat },
                            label = { Text("无限") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // 动作列表标题
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "动作列表 (${dialogActions.size}个)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (dialogActions.isNotEmpty()) {
                            TextButton(onClick = { dialogActions.clear() }) {
                                Text("清空", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // 显示动作列表
                    val actionsToShow = if (expandedActions) dialogActions else dialogActions.take(5)
                    actionsToShow.forEachIndexed { actionIndex, action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${actionIndex + 1}. ${actionSummary(action)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { dialogActions.removeAt(actionIndex) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("x", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (dialogActions.size > 5) {
                        TextButton(onClick = { expandedActions = !expandedActions }) {
                            Text(if (expandedActions) "收起" else "显示全部 ${dialogActions.size} 个动作")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 添加动作按钮
                    Text(
                        text = "添加动作:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { dialogKeyCode = ""; dialogShowKey = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+按键", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                        OutlinedButton(
                            onClick = { dialogDelayMs = ""; dialogShowDelay = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+延迟", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                        OutlinedButton(
                            onClick = {
                                dialogMouseHoldMs = ""
                                dialogMouseMode = "click"
                                dialogShowMouse = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+鼠标", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dialogName.isNotBlank()) {
                            if (dialogMode == DialogMode.CREATE) {
                                viewModel.saveNewMacro(
                                    dialogName.trim(),
                                    actions = dialogActions.toList(),
                                    repeatCount = dialogLoopCount,
                                    infiniteRepeat = dialogInfiniteRepeat,
                                    notes = dialogNotes.trim()
                                )
                            } else if (selectedMacroIndex >= 0 && selectedMacroIndex < macros.size) {
                                val macro = macros[selectedMacroIndex]
                                viewModel.updateMacroAll(
                                    macro.id,
                                    name = dialogName.trim(),
                                    actions = dialogActions.toList(),
                                    repeatCount = dialogLoopCount,
                                    infiniteRepeat = dialogInfiniteRepeat,
                                    notes = dialogNotes.trim()
                                )
                            }
                            showMacroDialog = false
                        }
                    },
                    enabled = dialogName.isNotBlank()
                ) {
                    Text(if (dialogMode == DialogMode.CREATE) "创建" else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMacroDialog = false }) {
                    Text("取消")
                }
            }
        )

        // === 子对话框：添加按键 ===
        if (dialogShowKey) {
            AlertDialog(
                onDismissRequest = { dialogShowKey = false; dialogKeyCode = "" },
                title = { Text("添加按键") },
                text = {
                    Column {
                        Text(
                            text = "输入按键键码 (如: A=65, Enter=10, Space=32):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dialogKeyCode,
                            onValueChange = { dialogKeyCode = it.filter { c -> c.isDigit() } },
                            label = { Text("键码") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val code = dialogKeyCode.toIntOrNull()
                            if (code != null && code > 0) {
                                dialogActions.add(MacroAction.KeyTap(keyCode = code, modifier = 0))
                            }
                            dialogKeyCode = ""
                            dialogShowKey = false
                        },
                        enabled = (dialogKeyCode.toIntOrNull() ?: 0) > 0
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogShowKey = false; dialogKeyCode = "" }) {
                        Text("取消")
                    }
                }
            )
        }

        // === 子对话框：添加延迟 ===
        if (dialogShowDelay) {
            AlertDialog(
                onDismissRequest = { dialogShowDelay = false; dialogDelayMs = "" },
                title = { Text("添加延迟") },
                text = {
                    Column {
                        Text(
                            text = "输入延迟时间（毫秒）:\n例如: 1000 = 1秒",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dialogDelayMs,
                            onValueChange = { dialogDelayMs = it.filter { c -> c.isDigit() } },
                            label = { Text("毫秒") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            suffix = { Text("ms") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val ms = dialogDelayMs.toIntOrNull()
                            if (ms != null && ms > 0) {
                                dialogActions.add(MacroAction.Delay(milliseconds = ms.toLong()))
                            }
                            dialogDelayMs = ""
                            dialogShowDelay = false
                        },
                        enabled = (dialogDelayMs.toIntOrNull() ?: 0) > 0
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogShowDelay = false; dialogDelayMs = "" }) {
                        Text("取消")
                    }
                }
            )
        }

        // === 子对话框：添加鼠标动作（点击 / 按下） ===
        if (dialogShowMouse) {
            var selectedButton by remember { mutableStateOf(HidReportBuilder.MouseButton.LEFT) }
            var selectedMode by remember { mutableStateOf(dialogMouseMode) }
            var holdMs by remember { mutableStateOf(dialogMouseHoldMs) }
            AlertDialog(
                onDismissRequest = {
                    dialogShowMouse = false
                    dialogMouseMode = "click"
                    dialogMouseHoldMs = ""
                },
                title = { Text("添加鼠标动作") },
                text = {
                    Column {
                        Text("选择鼠标按键:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedButton == HidReportBuilder.MouseButton.LEFT,
                                onClick = { selectedButton = HidReportBuilder.MouseButton.LEFT },
                                label = { Text("左键") }
                            )
                            FilterChip(
                                selected = selectedButton == HidReportBuilder.MouseButton.RIGHT,
                                onClick = { selectedButton = HidReportBuilder.MouseButton.RIGHT },
                                label = { Text("右键") }
                            )
                            FilterChip(
                                selected = selectedButton == HidReportBuilder.MouseButton.MIDDLE,
                                onClick = { selectedButton = HidReportBuilder.MouseButton.MIDDLE },
                                label = { Text("中键") }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("动作类型:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedMode == "click",
                                onClick = { selectedMode = "click" },
                                label = { Text("点击（按下+释放）") }
                            )
                            FilterChip(
                                selected = selectedMode == "press",
                                onClick = { selectedMode = "press" },
                                label = { Text("按下并保持") }
                            )
                        }
                        if (selectedMode == "press") {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = holdMs,
                                onValueChange = { holdMs = it.filter { c -> c.isDigit() } },
                                label = { Text("保持时间（毫秒）") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                suffix = { Text("ms") }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (selectedMode) {
                                "press" -> {
                                    val hold = holdMs.toLongOrNull() ?: 200
                                    dialogActions.add(MacroAction.MousePress(button = selectedButton, holdMs = hold))
                                }
                                else -> {
                                    dialogActions.add(MacroAction.MouseClick(button = selectedButton))
                                }
                            }
                            dialogShowMouse = false
                            dialogMouseMode = "click"
                            dialogMouseHoldMs = ""
                        }
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialogShowMouse = false
                        dialogMouseMode = "click"
                        dialogMouseHoldMs = ""
                    }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    // 删除确认对话框
    if (showDeleteConfirmation && selectedMacroIndex >= 0 && selectedMacroIndex < macros.size) {
        val macro = macros[selectedMacroIndex]
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除宏 \"${macro.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMacro(macro)
                        selectedMacroIndex = -1
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun actionSummary(action: MacroAction): String {
    return when (action) {
        is MacroAction.Delay -> "延迟 ${action.milliseconds}ms"
        is MacroAction.KeyPress -> "按键 ${action.keyCode} (修饰符: ${action.modifier})"
        is MacroAction.KeyTap -> "点击按键 ${action.keyCode}"
        is MacroAction.KeyRelease -> "释放按键"
        is MacroAction.MousePress -> "鼠标按下 ${buttonName(action.button)} (保持${action.holdMs}ms)"
        is MacroAction.MouseRelease -> "释放鼠标"
        is MacroAction.MouseClick -> "鼠标点击 ${buttonName(action.button)}"
        is MacroAction.MouseMove -> "鼠标移动 (${action.deltaX}, ${action.deltaY})"
        is MacroAction.MouseScroll -> "鼠标滚轮 ${action.delta}"
        is MacroAction.MouseMoveAbs -> "绝对移动 (${action.x}, ${action.y})"
        is MacroAction.TypeText -> "输入文本 \"${action.text}\""
        else -> "未知动作"
    }
}

private fun buttonName(button: Int): String {
    return when (button) {
        HidReportBuilder.MouseButton.LEFT -> "左键"
        HidReportBuilder.MouseButton.RIGHT -> "右键"
        HidReportBuilder.MouseButton.MIDDLE -> "中键"
        HidReportBuilder.MouseButton.BACK -> "后退键"
        HidReportBuilder.MouseButton.FORWARD -> "前进键"
        else -> "按键${button}"
    }
}