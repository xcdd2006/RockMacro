package com.example.rockmacro.macro

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class MacroRepository(context: Context) {

    companion object {
        private const val TAG = "MacroRepository"
        private const val PREFS_NAME = "rockmacro_macros"
        private const val KEY_MACROS = "macros"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveMacros(macros: List<Macro>) {
        try {
            val jsonArray = JSONArray()
            macros.forEach { macro ->
                jsonArray.put(macroToJson(macro))
            }
            prefs.edit().putString(KEY_MACROS, jsonArray.toString()).commit()
            Log.d(TAG, "Saved ${macros.size} macros")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save macros: ${e.message}")
        }
    }

    fun loadMacros(): List<Macro> {
        return try {
            val jsonStr = prefs.getString(KEY_MACROS, null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            val macros = mutableListOf<Macro>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                jsonToMacro(json)?.let { macros.add(it) }
            }
            Log.d(TAG, "Loaded ${macros.size} macros")
            macros
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load macros: ${e.message}")
            emptyList()
        }
    }

    private fun macroToJson(macro: Macro): JSONObject {
        return JSONObject().apply {
            put("id", macro.id)
            put("name", macro.name)
            put("notes", macro.notes)
            put("repeatCount", macro.repeatCount)
            put("infiniteRepeat", macro.infiniteRepeat)
            put("actions", JSONArray().apply {
                macro.actions.forEach { action ->
                    put(actionToJson(action))
                }
            })
        }
    }

    private fun jsonToMacro(json: JSONObject): Macro? {
        return try {
            val actionsArray = json.getJSONArray("actions")
            val actions = mutableListOf<MacroAction>()
            for (i in 0 until actionsArray.length()) {
                jsonToAction(actionsArray.getJSONObject(i))?.let { actions.add(it) }
            }
            Macro(
                id = json.getLong("id"),
                name = json.getString("name"),
                notes = json.optString("notes", ""),
                actions = actions,
                repeatCount = json.optInt("repeatCount", 1),
                infiniteRepeat = json.optBoolean("infiniteRepeat", false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse macro: ${e.message}")
            null
        }
    }

    private fun actionToJson(action: MacroAction): JSONObject {
        val json = JSONObject()
        when (action) {
            is MacroAction.KeyPress -> {
                json.put("type", "KeyPress")
                json.put("modifier", action.modifier)
                json.put("keyCode", action.keyCode)
                json.put("delayMs", action.delayMs)
            }
            is MacroAction.KeyRelease -> {
                json.put("type", "KeyRelease")
                json.put("delayMs", action.delayMs)
            }
            is MacroAction.KeyTap -> {
                json.put("type", "KeyTap")
                json.put("modifier", action.modifier)
                json.put("keyCode", action.keyCode)
                json.put("delayMs", action.delayMs)
            }
            is MacroAction.TypeText -> {
                json.put("type", "TypeText")
                json.put("text", action.text)
            }
            is MacroAction.MouseMove -> {
                json.put("type", "MouseMove")
                json.put("deltaX", action.deltaX.toInt())
                json.put("deltaY", action.deltaY.toInt())
            }
            is MacroAction.MousePress -> {
                json.put("type", "MousePress")
                json.put("button", action.button)
                json.put("holdMs", action.holdMs)
            }
            is MacroAction.MouseRelease -> {
                json.put("type", "MouseRelease")
                json.put("delayMs", action.delayMs)
            }
            is MacroAction.MouseClick -> {
                json.put("type", "MouseClick")
                json.put("button", action.button)
                json.put("delayMs", action.delayMs)
            }
            is MacroAction.MouseScroll -> {
                json.put("type", "MouseScroll")
                json.put("delta", action.delta.toInt())
            }
            is MacroAction.MouseMoveAbs -> {
                json.put("type", "MouseMoveAbs")
                json.put("x", action.x)
                json.put("y", action.y)
            }
            is MacroAction.Delay -> {
                json.put("type", "Delay")
                json.put("milliseconds", action.milliseconds)
            }
            is MacroAction.Repeat -> {
                json.put("type", "Repeat")
                json.put("times", action.times)
                json.put("actions", JSONArray().apply {
                    action.actions.forEach { put(actionToJson(it)) }
                })
            }
            is MacroAction.Loop -> {
                json.put("type", "Loop")
                json.put("isInfinite", action.isInfinite)
                json.put("times", action.times)
                json.put("actions", JSONArray().apply {
                    action.actions.forEach { put(actionToJson(it)) }
                })
            }
        }
        return json
    }

    private fun jsonToAction(json: JSONObject): MacroAction? {
        return try {
            when (val type = json.getString("type")) {
                "KeyPress" -> MacroAction.KeyPress(
                    modifier = json.optInt("modifier", 0),
                    keyCode = json.getInt("keyCode"),
                    delayMs = json.optLong("delayMs", 20)
                )
                "KeyRelease" -> MacroAction.KeyRelease(
                    delayMs = json.optLong("delayMs", 20)
                )
                "KeyTap" -> MacroAction.KeyTap(
                    modifier = json.optInt("modifier", 0),
                    keyCode = json.getInt("keyCode"),
                    delayMs = json.optLong("delayMs", 20)
                )
                "TypeText" -> MacroAction.TypeText(
                    text = json.getString("text")
                )
                "MouseMove" -> MacroAction.MouseMove(
                    deltaX = json.optInt("deltaX", 0).toByte(),
                    deltaY = json.optInt("deltaY", 0).toByte()
                )
                "MousePress" -> MacroAction.MousePress(
                    button = json.getInt("button"),
                    holdMs = json.optLong("holdMs", 0)
                )
                "MouseRelease" -> MacroAction.MouseRelease(
                    delayMs = json.optLong("delayMs", 20)
                )
                "MouseClick" -> MacroAction.MouseClick(
                    button = json.getInt("button"),
                    delayMs = json.optLong("delayMs", 20)
                )
                "MouseScroll" -> MacroAction.MouseScroll(
                    delta = json.optInt("delta", 0).toByte()
                )
                "MouseMoveAbs" -> MacroAction.MouseMoveAbs(
                    x = json.getInt("x"),
                    y = json.getInt("y")
                )
                "Delay" -> MacroAction.Delay(
                    milliseconds = json.getLong("milliseconds")
                )
                "Repeat" -> {
                    val subActions = mutableListOf<MacroAction>()
                    val subArray = json.getJSONArray("actions")
                    for (i in 0 until subArray.length()) {
                        jsonToAction(subArray.getJSONObject(i))?.let { subActions.add(it) }
                    }
                    MacroAction.Repeat(
                        times = json.getInt("times"),
                        actions = subActions
                    )
                }
                "Loop" -> {
                    val subActions = mutableListOf<MacroAction>()
                    val subArray = json.getJSONArray("actions")
                    for (i in 0 until subArray.length()) {
                        jsonToAction(subArray.getJSONObject(i))?.let { subActions.add(it) }
                    }
                    MacroAction.Loop(
                        isInfinite = json.optBoolean("isInfinite", false),
                        times = json.getInt("times"),
                        actions = subActions
                    )
                }
                else -> {
                    Log.w(TAG, "Unknown action type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse action: ${e.message}")
            null
        }
    }
}