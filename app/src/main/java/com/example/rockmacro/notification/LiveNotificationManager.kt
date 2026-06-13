package com.example.rockmacro.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
     * 构建宏运行状态的实况通知
     *
     * @param context       上下文
     * @param macroName     宏名称
     * @param isPlaying     是否正在运行（true=运行中, false=已暂停）
     * @param repeatInfo    重复次数信息（如 "共3次" / "无限循环"）
     * @param pendingIntent 点击通知跳转
     * @param pauseIntent   暂停按钮 Intent
     * @param resumeIntent  继续按钮 Intent
     * @param stopIntent    停止按钮 Intent
     */
    fun buildRunningNotification(
        context: Context,
        macroName: String,
        isPlaying: Boolean,
        pendingIntent: PendingIntent?,
        pauseIntent: PendingIntent?,
        resumeIntent: PendingIntent?,
        stopIntent: PendingIntent?
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
        }

        return builder.build()
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