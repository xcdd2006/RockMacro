package com.example.rockmacro.hid

object HidReportBuilder {

    // 键盘修饰键位掩码
    object KeyboardModifier {
        const val LEFT_CTRL = 0x01
        const val LEFT_SHIFT = 0x02
        const val LEFT_ALT = 0x04
        const val LEFT_GUI = 0x08
        const val RIGHT_CTRL = 0x10
        const val RIGHT_SHIFT = 0x20
        const val RIGHT_ALT = 0x40
        const val RIGHT_GUI = 0x80
    }

    // 鼠标按键位掩码
    object MouseButton {
        const val LEFT = 0x01
        const val RIGHT = 0x02
        const val MIDDLE = 0x04
        const val BACK = 0x08
        const val FORWARD = 0x10
    }

    // USB HID 键码表 (常用)
    object KeyCode {
        const val NONE = 0x00
        const val A = 0x04
        const val B = 0x05
        const val C = 0x06
        const val D = 0x07
        const val E = 0x08
        const val F = 0x09
        const val G = 0x0A
        const val H = 0x0B
        const val I = 0x0C
        const val J = 0x0D
        const val K = 0x0E
        const val L = 0x0F
        const val M = 0x10
        const val N = 0x11
        const val O = 0x12
        const val P = 0x13
        const val Q = 0x14
        const val R = 0x15
        const val S = 0x16
        const val T = 0x17
        const val U = 0x18
        const val V = 0x19
        const val W = 0x1A
        const val X = 0x1B
        const val Y = 0x1C
        const val Z = 0x1D

        const val NUM_1 = 0x1E
        const val NUM_2 = 0x1F
        const val NUM_3 = 0x20
        const val NUM_4 = 0x21
        const val NUM_5 = 0x22
        const val NUM_6 = 0x23
        const val NUM_7 = 0x24
        const val NUM_8 = 0x25
        const val NUM_9 = 0x26
        const val NUM_0 = 0x27

        const val ENTER = 0x28
        const val ESCAPE = 0x29
        const val BACKSPACE = 0x2A
        const val TAB = 0x2B
        const val SPACE = 0x2C
        const val MINUS = 0x2D
        const val EQUAL = 0x2E
        const val LEFT_BRACKET = 0x2F
        const val RIGHT_BRACKET = 0x30
        const val BACKSLASH = 0x31
        const val SEMICOLON = 0x33
        const val APOSTROPHE = 0x34
        const val GRAVE = 0x35
        const val COMMA = 0x36
        const val PERIOD = 0x37
        const val SLASH = 0x38
        const val CAPS_LOCK = 0x39

        const val F1 = 0x3A
        const val F2 = 0x3B
        const val F3 = 0x3C
        const val F4 = 0x3D
        const val F5 = 0x3E
        const val F6 = 0x3F
        const val F7 = 0x40
        const val F8 = 0x41
        const val F9 = 0x42
        const val F10 = 0x43
        const val F11 = 0x44
        const val F12 = 0x45

        const val PRINT_SCREEN = 0x46
        const val SCROLL_LOCK = 0x47
        const val PAUSE = 0x48
        const val INSERT = 0x49
        const val HOME = 0x4A
        const val PAGE_UP = 0x4B
        const val DELETE = 0x4C
        const val END = 0x4D
        const val PAGE_DOWN = 0x4E
        const val RIGHT_ARROW = 0x4F
        const val LEFT_ARROW = 0x50
        const val DOWN_ARROW = 0x51
        const val UP_ARROW = 0x52

        const val KEYPAD_NUMLOCK = 0x53
        const val KEYPAD_DIVIDE = 0x54
        const val KEYPAD_MULTIPLY = 0x55
        const val KEYPAD_SUBTRACT = 0x56
        const val KEYPAD_ADD = 0x57
        const val KEYPAD_ENTER = 0x58
        const val KEYPAD_1 = 0x59
        const val KEYPAD_2 = 0x5A
        const val KEYPAD_3 = 0x5B
        const val KEYPAD_4 = 0x5C
        const val KEYPAD_5 = 0x5D
        const val KEYPAD_6 = 0x5E
        const val KEYPAD_7 = 0x5F
        const val KEYPAD_8 = 0x60
        const val KEYPAD_9 = 0x61
        const val KEYPAD_0 = 0x62
        const val KEYPAD_DOT = 0x63
    }

    // 构建键盘报告 —— 复合描述符带 Report ID，第一字节是 ID
    fun buildKeyboardReport(modifier: Int, keyCodes: List<Int>): ByteArray {
        // 1 byte Report ID + 8 bytes 实际数据 = 9 bytes
        val report = ByteArray(1 + HidDescriptors.KEYBOARD_REPORT_SIZE)
        report[0] = HidDescriptors.COMPOSITE_REPORT_ID_KEYBOARD
        report[1] = modifier.toByte()
        report[2] = 0x00  // 保留字节
        for (i in 0 until minOf(keyCodes.size, 6)) {
            report[3 + i] = keyCodes[i].toByte()
        }
        return report
    }

    // 构建键盘弹起报告 (所有键释放) —— 带 Report ID
    fun buildKeyboardReleaseReport(): ByteArray {
        val report = ByteArray(1 + HidDescriptors.KEYBOARD_REPORT_SIZE)
        report[0] = HidDescriptors.COMPOSITE_REPORT_ID_KEYBOARD
        return report
    }

    // 构建鼠标报告 —— 复合描述符带 Report ID，第一字节是 ID
    fun buildMouseReport(
        buttons: Int,
        deltaX: Byte,
        deltaY: Byte,
        wheel: Byte
    ): ByteArray {
        // 1 byte Report ID + 4 bytes 实际数据 = 5 bytes
        val report = ByteArray(1 + HidDescriptors.MOUSE_REPORT_SIZE)
        report[0] = HidDescriptors.COMPOSITE_REPORT_ID_MOUSE
        report[1] = buttons.toByte()
        report[2] = deltaX
        report[3] = deltaY
        report[4] = wheel
        return report
    }

    // 构建鼠标静止报告 —— 带 Report ID
    fun buildMouseIdleReport(): ByteArray {
        val report = ByteArray(1 + HidDescriptors.MOUSE_REPORT_SIZE)
        report[0] = HidDescriptors.COMPOSITE_REPORT_ID_MOUSE
        return report
    }

    // 获取修饰键值
    fun getModifier(ctrl: Boolean = false, shift: Boolean = false,
                    alt: Boolean = false, gui: Boolean = false,
                    rightCtrl: Boolean = false, rightShift: Boolean = false,
                    rightAlt: Boolean = false, rightGui: Boolean = false): Int {
        var modifier = 0
        if (ctrl) modifier = modifier or KeyboardModifier.LEFT_CTRL
        if (shift) modifier = modifier or KeyboardModifier.LEFT_SHIFT
        if (alt) modifier = modifier or KeyboardModifier.LEFT_ALT
        if (gui) modifier = modifier or KeyboardModifier.LEFT_GUI
        if (rightCtrl) modifier = modifier or KeyboardModifier.RIGHT_CTRL
        if (rightShift) modifier = modifier or KeyboardModifier.RIGHT_SHIFT
        if (rightAlt) modifier = modifier or KeyboardModifier.RIGHT_ALT
        if (rightGui) modifier = modifier or KeyboardModifier.RIGHT_GUI
        return modifier
    }
}