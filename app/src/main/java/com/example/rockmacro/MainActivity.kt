package com.example.rockmacro

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rockmacro.hid.BluetoothHidService
import com.example.rockmacro.ui.*
import com.example.rockmacro.ui.theme.RockmacroTheme

class MainActivity : ComponentActivity() {

    var hidService by mutableStateOf<BluetoothHidService?>(null)
        private set
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothHidService.LocalBinder
            hidService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
            bound = false
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allBtGranted = permissions.filterKeys {
            it == Manifest.permission.BLUETOOTH_CONNECT || it == Manifest.permission.BLUETOOTH_SCAN
        }.values.all { it }
        if (allBtGranted) {
            startHidService()
        } else {
            Toast.makeText(this, "需要蓝牙权限才能使用此应用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求蓝牙权限并启动服务
        if (checkBluetoothPermissions()) {
            startHidService()
        } else {
            requestBluetoothPermissions()
        }

        setContent {
            RockmacroTheme {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val viewModel: MainViewModel = viewModel()
        var hidServiceReady by remember { mutableStateOf<BluetoothHidService?>(null) }

        // 当绑定完成时通知ViewModel
        LaunchedEffect(hidService) {
            hidServiceReady = hidService
            viewModel.bindHidService(hidService)
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()

        val tabs = listOf("蓝牙", "键盘", "鼠标", "宏")

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("RockMacro") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        // 连接状态指示器
                        val isConnected = connectedDeviceName != null
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isConnected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (isConnected) "已连接" else "未连接",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Text(
                                    when (index) {
                                        0 -> "📡"
                                        1 -> "⌨"
                                        2 -> "🖱"
                                        3 -> "⚡"
                                        else -> ""
                                    }
                                )
                            },
                            label = { Text(title) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> BluetoothScreen(viewModel)
                    1 -> KeyboardPanel(viewModel)
                    2 -> MousePanel(viewModel)
                    3 -> MacroScreen(viewModel)
                }
            }
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = mutableListOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            // Android 13+ needs notification permission for foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            bluetoothPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun startHidService() {
        val intent = Intent(this, BluetoothHidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }
}