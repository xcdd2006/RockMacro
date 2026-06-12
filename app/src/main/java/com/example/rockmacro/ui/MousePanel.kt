package com.example.rockmacro.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rockmacro.MainViewModel
import com.example.rockmacro.hid.HidReportBuilder
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun MousePanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mouse_settings", Context.MODE_PRIVATE) }
    var sensitivityDivider by remember {
        mutableFloatStateOf(prefs.getFloat("sensitivity", 8f))
    }
    // 灵敏度变化时自动保存
    LaunchedEffect(sensitivityDivider) {
        prefs.edit().putFloat("sensitivity", sensitivityDivider).apply()
    }

    var isFullscreen by remember { mutableStateOf(false) }

    // 全屏触摸板对话框
    if (isFullscreen) {
        FullscreenTouchpad(
            sensitivityDivider = sensitivityDivider,
            onMove = { dx, dy -> viewModel.mouseMove(dx, dy) },
            onDrag = { dx, dy -> viewModel.mouseDrag(HidReportBuilder.MouseButton.LEFT, dx, dy) },
            onLeftClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.LEFT) },
            onRightClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.RIGHT) },
            onScroll = { viewModel.mouseScroll(it) },
            onExit = { isFullscreen = false }
        )
    }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "触摸板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { isFullscreen = true }
            ) {
                Text("全屏", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 灵敏度滑块
        val sensitivityLabel = when {
            sensitivityDivider <= 2f -> "极高"
            sensitivityDivider <= 4f -> "超高"
            sensitivityDivider <= 6f -> "高"
            sensitivityDivider <= 9f -> "中"
            sensitivityDivider <= 13f -> "低"
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
                valueRange = 1f..20f,
                steps = 18,
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
            onRightClick = { viewModel.mouseClick(HidReportBuilder.MouseButton.RIGHT) },
            onScroll = { viewModel.mouseScroll(it) }
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
    onRightClick: () -> Unit,
    onScroll: (delta: Byte) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    // 跨协程共享的边缘移动状态（LaunchedEffect ↔ pointerInput 都运行在 Main 线程）
    val edgeState = remember { EdgeMoveState() }

    // 边缘自动移动协程——放在 Composable 级别的 LaunchedEffect 中
    LaunchedEffect(Unit) {
        while (true) {
            delay(25)
            val dx = edgeState.dx
            val dy = edgeState.dy
            if (dx != 0.toByte() || dy != 0.toByte()) {
                onMove(dx, dy)
            }
        }
    }

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
                    // 标记本次手势是否可能是双击（快速点击两次，第二次按下）
                    val isPotentialDoubleTap = !isDragging && gestureStartTime - lastTapTime < 280L
                    val edgeRatio = 0.12f
                    val edgeMinX = size.width * edgeRatio
                    val edgeMaxX = size.width * (1f - edgeRatio)
                    val edgeMinY = size.height * edgeRatio
                    val edgeMaxY = size.height * (1f - edgeRatio)
                    var prevCentroidY = 0f
                    var prevPointerCount = 0

                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        val pointerCount = activePointers.size

                        if (pointerCount > maxPointerCount) {
                            maxPointerCount = pointerCount
                        }

                        // ── 单指操作 ──
                        if (pointerCount == 1) {
                            val change = activePointers[0]
                            val pos = change.position
                            val prevPos = change.previousPosition
                            val dx = pos.x - prevPos.x
                            val dy = pos.y - prevPos.y

                            // 计算边缘自动移动方向
                            if (!gestureStartedDrag) {
                                val ex = when {
                                    pos.x < edgeMinX -> -(edgeMinX - pos.x) / edgeMinX
                                    pos.x > edgeMaxX -> (pos.x - edgeMaxX) / (size.width * edgeRatio)
                                    else -> 0f
                                }
                                val ey = when {
                                    pos.y > edgeMaxY -> (pos.y - edgeMaxY) / (size.height * edgeRatio)
                                    pos.y < edgeMinY -> -(edgeMinY - pos.y) / edgeMinY
                                    else -> 0f
                                }
                                edgeState.set(ex, ey)
                            }

                            if (dx != 0f || dy != 0f) {
                                totalMovement += kotlin.math.sqrt(dx * dx + dy * dy)

                                // ★ 关键：双击后滑动时才进入拖拽（仅移动超过阈值时激活）
                                if (isPotentialDoubleTap && !gestureStartedDrag &&
                                    totalMovement > 10f) {
                                    isDragging = true
                                    gestureStartedDrag = true
                                    onLeftClick() // 按下左键
                                    lastTapTime = 0L
                                }

                                val mdx = (dx / sensitivityDivider).roundToInt()
                                    .coerceIn(-127, 127).toByte()
                                val mdy = (dy / sensitivityDivider).roundToInt()
                                    .coerceIn(-127, 127).toByte()
                                if (mdx != 0.toByte() || mdy != 0.toByte()) {
                                    if (isDragging) {
                                        onDrag(mdx, mdy)
                                    } else {
                                        onMove(mdx, mdy)
                                    }
                                }
                                change.consume()
                            }

                        // ── 双指操作：滚轮 ──
                        } else if (pointerCount >= 2) {
                            edgeState.clear()
                            val centroidY = activePointers
                                .map { it.position.y }.average().toFloat()
                            val dy = if (prevPointerCount >= 2)
                                centroidY - prevCentroidY else 0f
                            prevCentroidY = centroidY

                            if (dy != 0f) {
                                totalMovement += kotlin.math.abs(dy)
                                val scrollDelta =
                                    (dy / 18f).roundToInt().coerceIn(-4, 4).toByte()
                                if (scrollDelta != 0.toByte()) {
                                    onScroll(scrollDelta)
                                }
                            }
                            event.changes.forEach { it.consume() }

                        } else {
                            event.changes.forEach { it.consume() }
                        }

                        prevPointerCount = pointerCount
                    } while (event.changes.any { it.pressed })

                    // 手指抬起——停止边缘移动
                    edgeState.clear()

                    // 抬起后判断操作类型
                    if (totalMovement < 20f) {
                        val now = System.currentTimeMillis()
                        if (maxPointerCount >= 2) {
                            // 双指点击 → 右键
                            onRightClick()
                            if (isDragging) {
                                isDragging = false
                                onLeftClick()
                            }
                            lastTapTime = 0L
                        } else {
                            if (isDragging && !gestureStartedDrag) {
                                // 拖拽中单击 → 释放
                                isDragging = false
                                onLeftClick()
                                lastTapTime = 0L
                            } else if (isPotentialDoubleTap && !gestureStartedDrag) {
                                // 双击松手（没滑动）→ 普通单击，不进入拖拽
                                onLeftClick()
                                lastTapTime = 0L
                            } else if (!gestureStartedDrag) {
                                // 普通单击
                                onLeftClick()
                                lastTapTime = now
                            }
                        }
                    } else {
                        // 有移动后抬起
                        if (gestureStartedDrag || isDragging) {
                            isDragging = false
                            onLeftClick() // 释放左键
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
            "触摸板\n" +
            "\n单指滑动  → 移动鼠标（边缘持续移动）" +
            "\n单指点击  → 左键" +
            "\n双击后滑动 → 拖拽" +
            "\n双指点击  → 右键" +
            "\n双指滑动  → 滚轮"
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

/**
 * 跨协程共享的边缘移动状态
 * pointerInput 手势协程更新方向值，LaunchedEffect 协程消费并发送移动事件
 * 所有访问都在 Main 线程，但用 @Volatile 保证跨 suspension 点的可见性
 */
private class EdgeMoveState {
    @Volatile
    private var _x = 0f
    @Volatile
    private var _y = 0f

    val dx: Byte get() = if (_x == 0f) 0 else (_x * 6f).roundToInt().coerceIn(-127, 127).toByte()
    val dy: Byte get() = if (_y == 0f) 0 else (_y * 6f).roundToInt().coerceIn(-127, 127).toByte()

    fun set(x: Float, y: Float) { _x = x; _y = y }
    fun clear() { _x = 0f; _y = 0f }
}

/**
 * 全屏触摸板——整个屏幕都是触摸板
 */
@Composable
private fun FullscreenTouchpad(
    sensitivityDivider: Float,
    onMove: (dx: Byte, dy: Byte) -> Unit,
    onDrag: (dx: Byte, dy: Byte) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (delta: Byte) -> Unit,
    onExit: () -> Unit
) {
    // 返回键退出全屏
    BackHandler(onBack = onExit)

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 全屏触摸板主体
            Touchpad(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 48.dp),
                sensitivityDivider = sensitivityDivider,
                onMove = onMove,
                onDrag = onDrag,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
                onScroll = onScroll
            )

            // 退出按钮
            Button(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "退出",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("退出全屏")
            }
        }
    }
}