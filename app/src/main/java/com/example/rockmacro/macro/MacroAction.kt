package com.example.rockmacro.macro

sealed class MacroAction {
    data class KeyPress(
        val modifier: Int = 0,
        val keyCode: Int,
        val delayMs: Long = 20
    ) : MacroAction()

    data class KeyRelease(
        val delayMs: Long = 20
    ) : MacroAction()

    data class KeyTap(
        val modifier: Int = 0,
        val keyCode: Int,
        val delayMs: Long = 20
    ) : MacroAction()

    data class TypeText(
        val text: String
    ) : MacroAction()

    data class MouseMove(
        val deltaX: Byte,
        val deltaY: Byte
    ) : MacroAction()

    data class MousePress(
        val button: Int,
        val holdMs: Long = 0
    ) : MacroAction()

    data class MouseRelease(
        val delayMs: Long = 20
    ) : MacroAction()

    data class MouseClick(
        val button: Int,
        val delayMs: Long = 20
    ) : MacroAction()

    data class MouseScroll(
        val delta: Byte
    ) : MacroAction()

    data class MouseMoveAbs(
        val x: Int,
        val y: Int
    ) : MacroAction()

    data class Delay(
        val milliseconds: Long
    ) : MacroAction()

    data class Repeat(
        val times: Int,
        val actions: List<MacroAction>
    ) : MacroAction()

    data class Loop(
        val isInfinite: Boolean,
        val times: Int,
        val actions: List<MacroAction>
    ) : MacroAction()
}

data class Macro(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val notes: String = "",
    val actions: List<MacroAction> = emptyList(),
    val repeatCount: Int = 1,
    val infiniteRepeat: Boolean = false
)