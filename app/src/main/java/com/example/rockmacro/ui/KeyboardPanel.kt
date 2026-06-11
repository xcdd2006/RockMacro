package com.example.rockmacro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rockmacro.MainViewModel
import com.example.rockmacro.hid.HidReportBuilder

@Composable
fun KeyboardPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "键盘控制",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 文本输入
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("输入文本") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.typeText(inputText)
                inputText = ""
            },
            enabled = inputText.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("发送文本")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 快捷按键
        Text(
            text = "快捷操作",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        QuickKeyRow(viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // 修饰键
        Text(
            text = "修饰键",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        ModifierKeysRow(viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // 导航键
        Text(
            text = "导航键",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        NavigationKeysGrid(viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // 功能键
        Text(
            text = "功能键",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        FunctionKeysRow(viewModel)
    }
}

@Composable
private fun QuickKeyRow(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickKey("Enter", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.ENTER)
        }
        QuickKey("ESC", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.ESCAPE)
        }
        QuickKey("Tab", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.TAB)
        }
        QuickKey("Space", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.SPACE)
        }
        QuickKey("Back", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.BACKSPACE)
        }
    }
}

@Composable
private fun ModifierKeysRow(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModifierKey("Ctrl", Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(
                HidReportBuilder.KeyboardModifier.LEFT_CTRL, HidReportBuilder.KeyCode.NONE
            )
        }
        ModifierKey("Shift", Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(
                HidReportBuilder.KeyboardModifier.LEFT_SHIFT, HidReportBuilder.KeyCode.NONE
            )
        }
        ModifierKey("Alt", Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(
                HidReportBuilder.KeyboardModifier.LEFT_ALT, HidReportBuilder.KeyCode.NONE
            )
        }
        ModifierKey("Win", Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(
                HidReportBuilder.KeyboardModifier.LEFT_GUI, HidReportBuilder.KeyCode.NONE
            )
        }
    }
}

@Composable
private fun NavigationKeysGrid(viewModel: MainViewModel) {
    // 方向键
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QuickKey("↑") {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.UP_ARROW)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuickKey("←") {
                viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.LEFT_ARROW)
            }
            QuickKey("↓") {
                viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.DOWN_ARROW)
            }
            QuickKey("→") {
                viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.RIGHT_ARROW)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickKey("Home", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.HOME)
        }
        QuickKey("End", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.END)
        }
        QuickKey("PgUp", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.PAGE_UP)
        }
        QuickKey("PgDn", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.PAGE_DOWN)
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickKey("Ins", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.INSERT)
        }
        QuickKey("Del", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.DELETE)
        }
        QuickKey("PrtSc", modifier = Modifier.weight(1f)) {
            viewModel.pressAndReleaseKey(0, HidReportBuilder.KeyCode.PRINT_SCREEN)
        }
    }
}

@Composable
private fun FunctionKeysRow(viewModel: MainViewModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 1..6) {
                QuickKey("F$i", modifier = Modifier.weight(1f)) {
                    val keyCode = HidReportBuilder.KeyCode.F1 + (i - 1)
                    viewModel.pressAndReleaseKey(0, keyCode)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 7..12) {
                QuickKey("F$i", modifier = Modifier.weight(1f)) {
                    val keyCode = HidReportBuilder.KeyCode.F1 + (i - 1)
                    viewModel.pressAndReleaseKey(0, keyCode)
                }
            }
        }
    }
}

@Composable
private fun QuickKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModifierKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}