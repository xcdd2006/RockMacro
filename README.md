# RockMacro - 蓝牙宏键盘应用

一个功能强大的 Android 蓝牙宏键盘应用，支持录制和播放复杂的键盘、鼠标宏操作。

## ✨ 功能特性

### 键盘宏
- 支持单个按键按下/释放
- 支持组合键（Ctrl、Shift、Alt 等修饰键）
- 支持文本输入
- 支持按键延迟设置

### 鼠标宏
- 支持鼠标移动（相对/绝对坐标）
- 支持鼠标点击（左键/右键/中键）
- 支持鼠标滚轮滚动
- 支持鼠标按下并保持

### 高级功能
- 宏循环执行（有限次/无限次）
- 动作重复执行
- 自定义延迟时间
- 宏保存与加载

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM
- **蓝牙协议**: HID (Human Interface Device)
- **构建工具**: Gradle

## 📱 设备要求

- Android 12 (API Level 31) 或更高版本
- 支持蓝牙功能的设备

## 🚀 快速开始

### 安装

1. 克隆仓库：
```bash
git clone https://github.com/your-username/rockmacro.git
cd rockmacro
```

2. 使用 Android Studio 打开项目

3. 构建并运行应用

### 使用方法

1. **连接蓝牙设备**
   - 打开应用，进入蓝牙连接页面
   - 扫描并连接目标设备

2. **创建宏**
   - 进入宏管理页面
   - 点击添加宏按钮
   - 输入宏名称和备注

3. **添加动作**
   - 选择宏进行编辑
   - 添加键盘按键、鼠标操作或延迟动作
   - 调整动作顺序

4. **播放宏**
   - 选择要执行的宏
   - 点击播放按钮开始执行

## 🏗️ 构建说明

### 本地构建

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 运行单元测试
./gradlew testDebugUnitTest

# 运行 Lint 检查
./gradlew lintDebug
```

### CI/CD

项目使用 GitHub Actions 进行持续集成：

- **自动构建**: 推送到 `main` 或 `develop` 分支时自动构建
- **自动测试**: 构建时自动运行单元测试和 Lint 检查
- **自动发布**: 创建 `v*` 标签时自动发布到 GitHub Releases

## 📁 项目结构

```
rockmacro/
├── app/
│   ├── src/main/java/com/example/rockmacro/
│   │   ├── MainActivity.kt          # 主活动
│   │   ├── MainViewModel.kt         # 主视图模型
│   │   ├── macro/
│   │   │   ├── MacroAction.kt       # 宏动作定义
│   │   │   ├── MacroEngine.kt       # 宏引擎
│   │   │   └── MacroRepository.kt   # 宏数据存储
│   │   ├── hid/
│   │   │   ├── BluetoothHidService.kt  # 蓝牙 HID 服务
│   │   │   ├── HidDescriptors.kt       # HID 描述符
│   │   │   └── HidReportBuilder.kt     # HID 报告构建器
│   │   ├── ui/
│   │   │   ├── MacroScreen.kt      # 宏管理界面
│   │   │   ├── BluetoothScreen.kt  # 蓝牙连接界面
│   │   │   ├── KeyboardPanel.kt    # 键盘面板
│   │   │   ├── MousePanel.kt       # 鼠标面板
│   │   │   └── theme/              # 主题配置
│   │   └── notification/           # 通知管理
│   └── src/main/res/               # 资源文件
├── .github/workflows/              # GitHub Actions 配置
├── gradle/                         # Gradle 配置
└── README.md
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 📧 联系方式

如有问题或建议，请通过以下方式联系：
- 提交 Issue
- 发送邮件

---

**注意**: 本应用需要蓝牙权限，请确保在使用时授予相应权限。