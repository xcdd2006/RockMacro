package com.example.rockmacro.hid

object HidDescriptors {

    val keyboardReportDescriptor: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),       // USAGE (Keyboard)
        0xA1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
        // 修饰键 (Modifier keys)
        0x05.toByte(), 0x07.toByte(),       //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),       //   USAGE_MINIMUM (Keyboard LeftControl)
        0x29.toByte(), 0xE7.toByte(),       //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
        0x95.toByte(), 0x08.toByte(),       //   REPORT_COUNT (8)
        0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)
        // 保留字节
        0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
        0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
        0x81.toByte(), 0x01.toByte(),       //   INPUT (Cnst,Arr,Abs)
        // LED输出报告
        0x95.toByte(), 0x05.toByte(),       //   REPORT_COUNT (5)
        0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
        0x05.toByte(), 0x08.toByte(),       //   USAGE_PAGE (LEDs)
        0x19.toByte(), 0x01.toByte(),       //   USAGE_MINIMUM (Num Lock)
        0x29.toByte(), 0x05.toByte(),       //   USAGE_MAXIMUM (Kana)
        0x91.toByte(), 0x02.toByte(),       //   OUTPUT (Data,Var,Abs)
        0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
        0x75.toByte(), 0x03.toByte(),       //   REPORT_SIZE (3)
        0x91.toByte(), 0x01.toByte(),       //   OUTPUT (Cnst,Arr,Abs)
        // 按键 (Keycodes)
        0x95.toByte(), 0x06.toByte(),       //   REPORT_COUNT (6)
        0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
        0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x65.toByte(),       //   LOGICAL_MAXIMUM (101)
        0x05.toByte(), 0x07.toByte(),       //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),       //   USAGE_MINIMUM (Reserved (no event indicated))
        0x29.toByte(), 0x65.toByte(),       //   USAGE_MAXIMUM (Keyboard Application)
        0x81.toByte(), 0x00.toByte(),       //   INPUT (Data,Arr,Abs)
        0xC0.toByte()                        // END_COLLECTION
    )

    val mouseReportDescriptor: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),       // USAGE (Mouse)
        0xA1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
        0x09.toByte(), 0x01.toByte(),       //   USAGE (Pointer)
        0xA1.toByte(), 0x00.toByte(),       //   COLLECTION (Physical)
        // 按键 (Buttons)
        0x05.toByte(), 0x09.toByte(),       //     USAGE_PAGE (Button)
        0x19.toByte(), 0x01.toByte(),       //     USAGE_MINIMUM (Button 1)
        0x29.toByte(), 0x05.toByte(),       //     USAGE_MAXIMUM (Button 5)
        0x15.toByte(), 0x00.toByte(),       //     LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),       //     LOGICAL_MAXIMUM (1)
        0x95.toByte(), 0x05.toByte(),       //     REPORT_COUNT (5)
        0x75.toByte(), 0x01.toByte(),       //     REPORT_SIZE (1)
        0x81.toByte(), 0x02.toByte(),       //     INPUT (Data,Var,Abs)
        0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
        0x75.toByte(), 0x03.toByte(),       //     REPORT_SIZE (3)
        0x81.toByte(), 0x01.toByte(),       //     INPUT (Cnst,Arr,Abs)
        // X轴和Y轴 (X and Y movement)
        0x05.toByte(), 0x01.toByte(),       //     USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),       //     USAGE (X)
        0x09.toByte(), 0x31.toByte(),       //     USAGE (Y)
        0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7F.toByte(),       //     LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
        0x95.toByte(), 0x02.toByte(),       //     REPORT_COUNT (2)
        0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)
        // 滚轮 (Wheel)
        0x09.toByte(), 0x38.toByte(),       //     USAGE (Wheel)
        0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7F.toByte(),       //     LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
        0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
        0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)
        0xC0.toByte(),                      //   END_COLLECTION
        0xC0.toByte()                        // END_COLLECTION
    )

    // 键盘报告大小: 1字节修饰键 + 1字节保留 + 6字节按键 = 8字节
    const val KEYBOARD_REPORT_SIZE = 8

    // 鼠标报告大小: 1字节按键 + 2字节X/Y + 1字节滚轮 = 4字节
    const val MOUSE_REPORT_SIZE = 4
}