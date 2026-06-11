package com.example.rockmacro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rockmacro.MainViewModel
import com.example.rockmacro.hid.HidReportBuilder

@Composable
fun MousePanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "鼠标控制",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 鼠标按键
        Text(
            text = "鼠标按键",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.LEFT) },
                modifier = Modifier.weight(1f)
            ) {
                Text("左键")
            }
            Button(
                onClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.MIDDLE) },
                modifier = Modifier.weight(1f)
            ) {
                Text("中键")
            }
            Button(
                onClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.RIGHT) },
                modifier = Modifier.weight(1f)
            ) {
                Text("右键")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 滚轮
        Text(
            text = "滚轮",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.mouseScroll(3) },
                modifier = Modifier.weight(1f)
            ) {
                Text("▲ 上滚")
            }
            Button(
                onClick = { viewModel.mouseScroll((-3).toByte()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("▼ 下滚")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 移动控制
        Text(
            text = "移动控制",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 方向键 (鼠标移动)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上
            Button(
                onClick = { viewModel.mouseMove(0, (-20).toByte()) },
                modifier = Modifier
                    .width(64.dp)
                    .height(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("▲")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 左
                Button(
                    onClick = { viewModel.mouseMove((-20).toByte(), 0) },
                    modifier = Modifier
                        .width(64.dp)
                        .height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("◄")
                }

                // 中 (归位按扭)
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier
                        .width(64.dp)
                        .height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("●")
                }

                // 右
                Button(
                    onClick = { viewModel.mouseMove(20.toByte(), 0) },
                    modifier = Modifier
                        .width(64.dp)
                        .height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("►")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 下
            Button(
                onClick = { viewModel.mouseMove(0, 20.toByte()) },
                modifier = Modifier
                    .width(64.dp)
                    .height(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("▼")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 移动步长
        Text(
            text = "快速移动",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FastMoveButton("↖", (-40).toByte(), (-40).toByte(), viewModel, Modifier.weight(1f))
            FastMoveButton("↑", 0, (-40).toByte(), viewModel, Modifier.weight(1f))
            FastMoveButton("↗", 40.toByte(), (-40).toByte(), viewModel, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FastMoveButton("←", (-40).toByte(), 0, viewModel, Modifier.weight(1f))
            FastMoveButton("→", 40.toByte(), 0, viewModel, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FastMoveButton("↙", (-40).toByte(), 40.toByte(), viewModel, Modifier.weight(1f))
            FastMoveButton("↓", 0, 40.toByte(), viewModel, Modifier.weight(1f))
            FastMoveButton("↘", 40.toByte(), 40.toByte(), viewModel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FastMoveButton(
    label: String,
    dx: Byte,
    dy: Byte,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { viewModel.mouseMove(dx, dy) },
        modifier = modifier
    ) {
        Text(label)
    }
}