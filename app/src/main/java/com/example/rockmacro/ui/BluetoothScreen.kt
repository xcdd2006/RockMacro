package com.example.rockmacro.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rockmacro.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun BluetoothScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val hidReady by viewModel.hidReady.collectAsState()

    val context = LocalContext.current
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 蓝牙已启用，自动开始扫描
            if (!hidReady) {
                viewModel.initHidService()
            }
            viewModel.startScan()
        }
    }

    // 请求经典蓝牙可发现模式（让PC等设备能扫描到）
    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.resultCode == Activity.RESULT_CANCELED) {
            // 无论用户选择什么，都继续启动HID广播（包括BLE）
            viewModel.makeDiscoverable()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 连接状态卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (connectedDeviceName != null)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "连接状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        connectedDeviceName != null -> "已连接: $connectedDeviceName"
                        connectionState == MainViewModel.ConnectionState.CONNECTING -> "正在连接..."
                        connectionState == MainViewModel.ConnectionState.CONNECTED -> "已连接（未选择设备）"
                        connectionState == MainViewModel.ConnectionState.SCANNING -> "正在扫描..."
                        connectionState == MainViewModel.ConnectionState.ADVERTISING -> "正在广播..."
                        connectionState == MainViewModel.ConnectionState.ERROR -> "错误"
                        else -> "未连接"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                if (errorMessage != null) {
                    Text(
                        text = "错误: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 广播控制
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (connectionState == MainViewModel.ConnectionState.ADVERTISING)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "蓝牙广播",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (connectionState == MainViewModel.ConnectionState.ADVERTISING)
                        "正在广播HID服务，其他设备可发现此设备"
                    else
                        "广播后其他蓝牙设备可以发现并连接此设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // 先请求经典蓝牙可发现模式（让PC能扫描到）
                            val intent = viewModel.createDiscoverableIntent()
                            if (intent != null) {
                                discoverableLauncher.launch(intent)
                            } else {
                                // 如果无法创建Intent，直接启动HID广播
                                viewModel.makeDiscoverable()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = connectionState != MainViewModel.ConnectionState.ADVERTISING
                    ) {
                        Text("开始广播")
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopAdvertising() },
                        modifier = Modifier.weight(1f),
                        enabled = connectionState == MainViewModel.ConnectionState.ADVERTISING
                    ) {
                        Text("停止广播")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (connectionState == MainViewModel.ConnectionState.SCANNING) {
                        viewModel.stopScan()
                    } else {
                        // 检查蓝牙是否开启
                        val btAdapter = viewModel.bluetoothAdapter
                        if (btAdapter?.isEnabled == true) {
                            if (!hidReady) {
                                viewModel.initHidService()
                            }
                            viewModel.startScan()
                        } else {
                            // 请求开启蓝牙
                            try {
                                enableBluetoothLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                )
                            } catch (e: Exception) {
                                viewModel.setErrorMessage("无法开启蓝牙: ${e.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                if (connectionState == MainViewModel.ConnectionState.SCANNING) {
                    Text("取消扫描")
                } else {
                    Text("扫描设备")
                }
            }

            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.weight(1f),
                enabled = connectedDeviceName != null
            ) {
                Text("断开连接")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 配对设备列表（限制最大高度，避免遮挡扫描结果）
        Text(
            text = "配对设备",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp)
        ) {
            items(pairedDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = device.name.ifEmpty { "未知设备" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (connectedDeviceName?.contains(device.name) == true || 
                            connectedDeviceName?.contains(device.address) == true) {
                            Text(
                                text = "已连接",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(
                                onClick = {
                                    if (connectionState != MainViewModel.ConnectionState.CONNECTING) {
                                        viewModel.connectToDevice(device.device)
                                    }
                                },
                                enabled = connectionState != MainViewModel.ConnectionState.CONNECTING
                            ) {
                                Text("连接")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 扫描结果（占用剩余空间）
        Text(
            text = "扫描结果",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(scannedDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable {
                                if (connectionState != MainViewModel.ConnectionState.CONNECTING) {
                                    viewModel.connectToDevice(device.device)
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = device.name.ifEmpty { "未知设备" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Button(
                            onClick = { 
                                if (connectionState != MainViewModel.ConnectionState.CONNECTING) {
                                    viewModel.connectToDevice(device.device)
                                }
                            },
                            enabled = connectionState != MainViewModel.ConnectionState.CONNECTING
                        ) {
                            Text("连接")
                        }
                    }
                }
            }
        }
    }
}