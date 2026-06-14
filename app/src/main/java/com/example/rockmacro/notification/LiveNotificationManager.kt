package com.example.rockmacro.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.example.rockmacro.R

/**
 * 宏实况通知管理器
 *
 * 使用 IMPORTANCE_HIGH 渠道实现实时通知效果：
 * - 通知会以横幅（heads-up）形式弹出
 * - 配合 setOngoing(true) 持续显示
 * - 支持操作按钮（暂停/继续/停止）
 *
 * 同时支持 MIUI/HyperOS 超级岛（Super Island）通知，
 * 通过 miui.focus.* extras 实现岛状展示。
 */
object LiveNotificationManager {

    private const val TAG = "LiveNotificationMgr"

    const val CHANNEL_LIVE_UPDATE = "macro_live_channel"

    // ========================================================================
    // 通知渠道
    // ========================================================================

    /** 创建宏实况通知渠道 */
    fun createLiveUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_LIVE_UPDATE,
                "宏实况更新",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
            Log.d(TAG, "宏实况更新渠道已创建 (IMPORTANCE_HIGH)")
        }
    }

    // ========================================================================
    // 构建通知
    // ========================================================================

    /**
     * 构建宏运行状态的实况通知（含 MIUI 超级岛支持）
     *
     * @param context       上下文
     * @param macroName     宏名称
     * @param isPlaying     是否正在运行（true=运行中, false=已暂停）
     * @param pendingIntent 点击通知跳转
     * @param pauseIntent   暂停按钮 Intent
     * @param resumeIntent  继续按钮 Intent
     * @param stopIntent    停止按钮 Intent
     * @param enableSuperIsland 是否启用 MIUI 超级岛（默认 true）
     */
    fun buildRunningNotification(
        context: Context,
        macroName: String,
        isPlaying: Boolean,
        pendingIntent: PendingIntent?,
        pauseIntent: PendingIntent?,
        resumeIntent: PendingIntent?,
        stopIntent: PendingIntent?,
        enableSuperIsland: Boolean = true
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_LIVE_UPDATE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val stateText = if (isPlaying) "播放中" else "已暂停"

        builder.apply {
            setContentTitle(stateText)
            setContentText("宏: $macroName")
            setSmallIcon(R.mipmap.icon_foreground)
            setOngoing(true)
            setAutoCancel(false)
            setShowWhen(true)
            setOnlyAlertOnce(true)
            pendingIntent?.let { setContentIntent(it) }

            // Android 15+ 实况通知（Promoted Ongoing Notification）
            if (Build.VERSION.SDK_INT >= 35) {
                val extras = Bundle()
                extras.putBoolean("android.requestPromotedOngoing", true)
                addExtras(extras)
            }

            // 操作按钮
            if (isPlaying) {
                pauseIntent?.let {
                    addAction(android.R.drawable.ic_media_pause, "暂停", it)
                }
                stopIntent?.let {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", it)
                }
            } else {
                resumeIntent?.let {
                    addAction(android.R.drawable.ic_media_play, "继续", it)
                }
                stopIntent?.let {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", it)
                }
            }

            // ================================================================
            // MIUI / HyperOS 超级岛集成
            // ================================================================
            if (enableSuperIsland) {
                val islandExtras = buildSuperIslandExtras(
                    context = context,
                    macroName = macroName,
                    isPlaying = isPlaying,
                    pauseIntent = pauseIntent,
                    resumeIntent = resumeIntent,
                    stopIntent = stopIntent
                )
                addExtras(islandExtras)
            }
        }

        return builder.build()
    }

    // ========================================================================
    // MIUI 超级岛
    // ========================================================================

    /**
     * 构建超级岛的 extras Bundle（miui.focus.*）
     *
     * 包含：
     * - miui.focus.param: 岛布局参数的 JSON 字符串
     * - miui.focus.actions: 按钮 PendingIntent 映射
     * - miui.focus.pics: 图标资源映射
     */
    private fun buildSuperIslandExtras(
        context: Context,
        macroName: String,
        isPlaying: Boolean,
        pauseIntent: PendingIntent?,
        resumeIntent: PendingIntent?,
        stopIntent: PendingIntent?
    ): Bundle {
        val extras = Bundle()

        // 1. 构建岛参数 JSON
        val islandParams = buildIslandParamsJson(macroName, isPlaying)
        extras.putString("miui.focus.param", islandParams)

        // 2. 构建按钮 Action 映射（miui.focus.actions）
        //    官方模板：actions 数组中的 "action" 字段引用这里的 key
        val actionsBundle = Bundle()

        // 按钮1：暂停/继续
        val toggleActionKey = "miui.focus.action_pause_resume"
        if (isPlaying) {
            pauseIntent?.let {
                // 注意：官方文档要求广播 PendingIntent 添加 FLAG_RECEIVER_FOREGROUND
                val pauseAction = Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                    "暂停",
                    it
                ).build()
                actionsBundle.putParcelable(toggleActionKey, pauseAction)
            }
        } else {
            resumeIntent?.let {
                val resumeAction = Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_play),
                    "继续",
                    it
                ).build()
                actionsBundle.putParcelable(toggleActionKey, resumeAction)
            }
        }

        // 按钮2：停止
        val stopActionKey = "miui.focus.action_stop"
        stopIntent?.let {
            val stopAction = Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                "停止",
                it
            ).build()
            actionsBundle.putParcelable(stopActionKey, stopAction)
        }

        extras.putBundle("miui.focus.actions", actionsBundle)

        // 3. 构建图片资源映射
        val picsBundle = Bundle()
        picsBundle.putParcelable(
            "miui.focus.pic_imageText",
            Icon.createWithResource(context, R.mipmap.icon_foreground)
        )
        picsBundle.putParcelable(
            "miui.focus.pic_large_v2",
            Icon.createWithResource(context, R.mipmap.icon_foreground)
        )
        picsBundle.putParcelable(
            "miui.focus.pic_ticker",
            Icon.createWithResource(context, R.mipmap.icon_foreground)
        )
        picsBundle.putParcelable(
            "miui.focus.pic_aod",
            Icon.createWithResource(context, R.mipmap.icon_foreground)
        )
        extras.putBundle("miui.focus.pics", picsBundle)

        return extras
    }

    /**
     * 构建 MIUI 超级岛布局参数的 JSON 字符串
     *
     * 基于官方模板，只替换关键文字和 Intent：
     * - bigIslandArea 大岛布局
     * - iconTextInfo 展开态（严格按照模板颜色）
     * - smallIslandArea 小岛（胶囊态）
     * - textButton 按钮（替换文字和 Intent）
     */
    private fun buildIslandParamsJson(macroName: String, isPlaying: Boolean): String {
        val stateText = if (isPlaying) "运行中" else "已暂停"
        val toggleText = if (isPlaying) "暂停" else "继续"
        val toggleAction = if (isPlaying) "com.example.rockmacro.action.MACRO_PAUSE" else "com.example.rockmacro.action.MACRO_RESUME"

        return """
{
    "param_v2": {
        "protocol": 1,
        "business": "macro",
        "enableFloat": true,
        "updatable": true,
        "ticker": "${macroName} ${stateText}",
        "tickerPic": "miui.focus.pic_ticker",
        "aodTitle": "${macroName}",
        "aodPic": "miui.focus.pic_aod",
        "actions": [
            {
                "action": "miui.focus.action_pause_resume"
            },
            {
                "action": "miui.focus.action_stop"
            }
        ],
        "param_island": {
            "islandProperty": 1,
            "bigIslandArea": {
                "imageTextInfoLeft": {
                    "type": 1,
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_imageText"
                    }
                },
                "imageTextInfoRight": {
                    "type": 2,
                    "textInfo": {
                        "title": "${stateText}"
                    }
                }
            },
            "smallIslandArea": {
                "picInfo": {
                    "type": 1,
                    "pic": "miui.focus.pic_imageText"
                }
            },
            "shareData": {
                "title": "${macroName}"
            }
        },
        "iconTextInfo": {
            "title": "${macroName}",
            "content": "${stateText}",
            "colorTitle": "#000000",
            "colorTitleDark": "#FFFFFF",
            "colorContent": "#000000",
            "colorContentDark": "#FFFFFF",
            "animIconInfo": {
                "type": 3,
                "src": "miui.focus.pic_large_v2"
            }
        },
        "picInfo": {
            "pic": "miui.focus.pic_large_v2",
            "type": 1
        },
        "textButton": [
            {
                "action": "miui.focus.action_pause_resume",
                "actionTitle": "${toggleText}",
                "actionTitleColor": "#FFFFFF",
                "actionBgColor": "#006EFF"
            },
            {
                "action": "miui.focus.action_stop",
                "actionTitle": "停止",
                "actionTitleColor": "#FFFFFF",
                "actionBgColor": "#006EFF"
            }
        ],
        "baseInfo": {
            "title": "${macroName}",
            "content": "${stateText}",
            "colorTitle": "#006EFF",
            "type": 2
        },
        "hintInfo": {
            "type": 1,
            "title": "${macroName} ${stateText}",
            "actionInfo": {
                "action": "miui.focus.action_pause_resume"
            }
        },
        "extraInfo": {
            "app": "RockMacro",
            "version": "1.0"
        }
    }
}
""".trimIndent()
    }

    // ========================================================================
    // 通知操作
    // ========================================================================

    fun notify(context: Context, id: Int, notification: Notification) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    fun cancel(context: Context, id: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id)
    }
}