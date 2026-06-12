package com.example.rockmacro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rockmacro.MainViewModel
import com.example.rockmacro.hid.HidReportBuilder
import kotlin.math.roundToInt

@Composable
fun MousePanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var sensitivityDivider by remember { mutableFloatStateOf(8f) }

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

        // 触摸板
        Text(
            text = "触摸板",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 灵敏度滑块
        val sensitivityLabel = when {
            sensitivityDivider <= 3f -> "超高"
            sensitivityDivider <= 5f -> "高"
            sensitivityDivider <= 8f -> "中"
            sensitivityDivider <= 12f -> "低"
            else -> "超低"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "灵敏度",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Slider(
                value = sensitivityDivider,
                onValueChange = { sensitivityDivider = it },
                valueRange = 2f..20f,
                steps = 17,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = sensitivityLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Touchpad(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            sensitivityDivider = sensitivityDivider,
            onMove = { dx, dy -> viewModel.mouseMove(dx, dy) },
            onDrag = { dx, dy -> viewModel.mouseDrag(HidReportBuilder.MouseButton.LEFT, dx, dy) },
            onLeftClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.LEFT) },
            onRightClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.RIGHT) }
        )
    }
}

@Composable
private fun Touchpad(
    modifier: Modifier = Modifier,
    sensitivityDivider: Float = 8f,
    onMove: (dx: Byte, dy: Byte) -> Unit,
    onDrag: (dx: Byte, dy: Byte) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(
                color = if (isDragging)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isDragging) 2.dp else 1.dp,
                color = if (isDragging)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(sensitivityDivider) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val gestureStartTime = System.currentTimeMillis()
                    var maxPointerCount = 1
                    var totalMovement = 0f
                    var gestureStartedDrag = false

                    // ★ 关键修复：第二次按下时立即进入拖拽，而不是等抬起
                    if (!isDragging && gestureStartTime - lastTapTime < 500L) {
                        isDragging = true
                        gestureStartedDrag = true
                        onLeftClick() // 按下左键
                        lastTapTime = 0L
                    }

                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        val pointerCount = activePointers.size

                        if (pointerCount > maxPointerCount) {
                            maxPointerCount = pointerCount
                        }

                        // 单指滑动 → 鼠标移动
                        if (pointerCount == 1) {
                            val change = activePointers[0]
                            val currentPos = change.position
                            val previousPos = change.previousPosition
                            val dx = currentPos.x - previousPos.x
                            val dy = currentPos.y - previousPos.y
                            if (dx != 0f || dy != 0f) {
                                totalMovement += kotlin.math.sqrt(dx * dx + dy * dy)
                                val mdx = (dx / sensitivityDivider).roundToInt()
                                    .coerceIn(-127, 127).toByte()
                                val mdy = (dy / sensitivityDivider).roundToInt()
                                    .coerceIn(-127, 127).toByte()
                                if (mdx != 0.toByte() || mdy != 0.toByte()) {
                                    if (isDragging) {
                                        onDrag(mdx, mdy)  // 拖拽：按住左键移动
                                    } else {
                                        onMove(mdx, mdy)  // 普通：仅移动
                                    }
                                }
                                change.consume()
                            }
                        } else {
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    // 手指抬起后判断
                    if (totalMovement < 20f) {
                        val now = System.currentTimeMillis()
                        if (maxPointerCount >= 2) {
                            // 双指点击 → 右键
                            onRightClick()
                            if (isDragging) {
                                isDragging = false
                                onLeftClick()  // 释放左键
                            }
                            lastTapTime = 0L
                        } else {
                            // 单指点击
                            if (isDragging && !gestureStartedDrag) {
                                // 拖拽中单击 → 释放
                                isDragging = false
                                onLeftClick()
                                lastTapTime = 0L
                            } else if (!gestureStartedDrag) {
                                // 普通单击 → 记录时间用于双击检测
                                onLeftClick()
                                lastTapTime = now
                            }
                            // gestureStartedDrag = 双击进入拖拽但没移动：保持拖拽状态
                        }
                    } else {
                        // 有移动后抬起：如果是拖拽操作，释放左键
                        if (gestureStartedDrag || isDragging) {
                            isDragging = false
                            onLeftClick()  // 释放左键
                        }
                        lastTapTime = 0L
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val hintText = if (isDragging) {
            "拖拽中…\n\n滑动 → 拖拽\n点击 → 释放"
        } else {
            "触摸板\n\n单指滑动 → 移动鼠标\n单指点击 → 左键\n双击后滑动 → 拖拽\n双指点击 → 右键"
        }
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDragging)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal
        )
    }
}