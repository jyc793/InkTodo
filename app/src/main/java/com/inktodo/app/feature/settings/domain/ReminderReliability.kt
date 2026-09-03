package com.inktodo.app.feature.settings.domain

enum class ReliabilityState { ENABLED, DISABLED, SYSTEM_LIMITED }

enum class ReliabilityAction {
    NONE,
    REQUEST_NOTIFICATION_PERMISSION,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_NOTIFICATION_CHANNEL_SETTINGS,
    OPEN_EXACT_ALARM_SETTINGS,
    OPEN_FULL_SCREEN_SETTINGS,
    OPEN_OVERLAY_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_HYPEROS_AUTOSTART,
    OPEN_APP_DETAILS,
}

data class ReliabilityItem(
    val id: String,
    val title: String,
    val description: String,
    val state: ReliabilityState,
    val action: ReliabilityAction,
    val guidance: String? = null,
    val channelId: String? = null,
)

data class ReminderChannelCapability(
    val id: String,
    val title: String,
    val importance: Int,
    val requiredImportance: Int,
    val soundRequired: Boolean,
    val soundEnabled: Boolean,
    val vibrationRequired: Boolean,
    val vibrationEnabled: Boolean,
) {
    val ready: Boolean = importance >= requiredImportance &&
        (!soundRequired || soundEnabled) &&
        (!vibrationRequired || vibrationEnabled)
}

data class ReminderReliabilitySnapshot(val items: List<ReliabilityItem>) {
    val disabledCount: Int = items.count { it.state == ReliabilityState.DISABLED }
    val confirmationCount: Int = items.count { it.state == ReliabilityState.SYSTEM_LIMITED }
    val summary: String = when {
        disabledCount > 0 && confirmationCount > 0 ->
            "提醒可靠性：有 ${disabledCount} 项需要设置，${confirmationCount} 项需要确认"
        disabledCount > 0 -> "提醒可靠性：有 ${disabledCount} 项需要设置"
        confirmationCount > 0 -> "核心能力良好 · ${confirmationCount} 项需手动确认"
        else -> "提醒可靠性：良好"
    }
}

data class PlatformReminderCapabilities(
    val notificationPermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val exactAlarmsEnabled: Boolean,
    val fullScreenIntentEnabled: Boolean,
    val strongNotificationChannelsEnabled: Boolean,
    val batteryUnrestricted: Boolean,
    val systemClockAvailable: Boolean,
    val isHyperOsDevice: Boolean,
    val notificationChannels: List<ReminderChannelCapability> = emptyList(),
    val unhealthyReminderCount: Int = 0,
    val lastReminderHealthDetail: String? = null,
    val overlayPermissionGranted: Boolean = true,
)

object ReminderReliabilityPolicy {
    fun evaluate(capabilities: PlatformReminderCapabilities): ReminderReliabilitySnapshot {
        val notificationsReady = capabilities.notificationPermissionGranted &&
            capabilities.appNotificationsEnabled
        return ReminderReliabilitySnapshot(
            buildList {
                add(
                    ReliabilityItem(
                        "notifications",
                        "通知权限",
                        if (notificationsReady) {
                            "预提醒、正式提醒和持续通知可以由系统展示。"
                        } else {
                            "未开启时，闹钟可能触发，但 App 无法显示提醒通知。"
                        },
                        enabledState(notificationsReady),
                        if (!capabilities.notificationPermissionGranted) {
                            ReliabilityAction.REQUEST_NOTIFICATION_PERMISSION
                        } else {
                            ReliabilityAction.OPEN_NOTIFICATION_SETTINGS
                        },
                    ),
                )
                add(
                    ReliabilityItem(
                        "exact_alarm",
                        "精确闹钟能力",
                        "用于在指定时刻注册预提醒、正式提醒和持续提醒边界。",
                        enabledState(capabilities.exactAlarmsEnabled),
                        ReliabilityAction.OPEN_EXACT_ALARM_SETTINGS,
                    ),
                )
                add(
                    ReliabilityItem(
                        "full_screen",
                        "全屏强提醒",
                        "系统允许时用于正式提醒；未开启仍会退回高优先级通知。",
                        enabledState(capabilities.fullScreenIntentEnabled),
                        ReliabilityAction.OPEN_FULL_SCREEN_SETTINGS,
                    ),
                )
                add(
                    ReliabilityItem(
                        "overlay",
                        "页面上层确认框",
                        "解锁使用手机时，让正式提醒直接覆盖当前页面，无需点击通知。",
                        enabledState(capabilities.overlayPermissionGranted),
                        ReliabilityAction.OPEN_OVERLAY_SETTINGS,
                        if (capabilities.overlayPermissionGranted) null
                        else "请允许墨待办显示在其他应用上层；这是解锁状态主动弹框所必需的系统特殊权限。",
                    ),
                )
                if (capabilities.notificationChannels.isEmpty()) {
                    add(
                        ReliabilityItem(
                            "strong_channels",
                            "正式提醒声音与悬浮通知",
                            "检查正式提醒和持续提醒通道是否仍为高优先级并保留声音。",
                            enabledState(capabilities.strongNotificationChannelsEnabled),
                            ReliabilityAction.OPEN_NOTIFICATION_SETTINGS,
                        ),
                    )
                } else {
                    capabilities.notificationChannels.forEach { channel ->
                        add(
                            ReliabilityItem(
                                id = "channel_${channel.id}",
                                title = channel.title,
                                description = if (channel.ready) {
                                    "此提醒通道的重要性、声音与振动符合当前用途。"
                                } else {
                                    "此通道被静音、降级或关闭，可能导致到时无声或没有顶部提示。"
                                },
                                state = enabledState(channel.ready),
                                action = ReliabilityAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS,
                                guidance = if (channel.ready) null else "请在系统页面恢复此通道的提醒方式。",
                                channelId = channel.id,
                            ),
                        )
                    }
                }
                add(
                    ReliabilityItem(
                        "reminder_health",
                        "最近提醒执行状态",
                        if (capabilities.unhealthyReminderCount == 0) {
                            "没有检测到未恢复的提醒注册或投递失败。"
                        } else {
                            "有 ${capabilities.unhealthyReminderCount} 条提醒需要重新检查。"
                        },
                        enabledState(capabilities.unhealthyReminderCount == 0),
                        if (capabilities.unhealthyReminderCount == 0) ReliabilityAction.NONE
                        else ReliabilityAction.OPEN_NOTIFICATION_SETTINGS,
                        capabilities.lastReminderHealthDetail,
                    ),
                )
                add(
                    ReliabilityItem(
                        "lock_screen",
                        "锁屏通知",
                        "Android 不允许 App 读取 HyperOS 的锁屏通知开关，请在通知设置中确认。",
                        ReliabilityState.SYSTEM_LIMITED,
                        ReliabilityAction.OPEN_NOTIFICATION_SETTINGS,
                        "请确认“锁屏通知”和“悬浮通知”已允许。",
                    ),
                )
                add(
                    ReliabilityItem(
                        "battery_unrestricted",
                        "后台无限制",
                        "减少息屏和长时间后台时被电池策略延迟的风险。",
                        enabledState(capabilities.batteryUnrestricted),
                        ReliabilityAction.OPEN_BATTERY_SETTINGS,
                        "在系统电池设置中将墨待办设为“不限制”或等效选项。",
                    ),
                )
                if (capabilities.isHyperOsDevice) {
                    add(
                        ReliabilityItem(
                            "hyperos_autostart",
                            "HyperOS 自启动",
                            "HyperOS 没有公开读取接口，需要在系统自启动页面手动确认。",
                            ReliabilityState.SYSTEM_LIMITED,
                            ReliabilityAction.OPEN_HYPEROS_AUTOSTART,
                            "请允许墨待办自启动；若页面不可用，将打开应用详情页。",
                        ),
                    )
                    add(
                        ReliabilityItem(
                            "hyperos_background",
                            "HyperOS 后台策略",
                            "厂商后台策略状态无法通过合法 Android API 完整读取。",
                            ReliabilityState.SYSTEM_LIMITED,
                            ReliabilityAction.OPEN_BATTERY_SETTINGS,
                            "请确认后台运行和电池策略均允许，不要一键清理本 App。",
                        ),
                    )
                }
                add(
                    ReliabilityItem(
                        "system_clock",
                        "系统闹钟应用",
                        if (capabilities.systemClockAvailable) {
                            "已找到可处理官方系统闹钟请求的时钟应用。"
                        } else {
                            "未找到可处理官方系统闹钟请求的应用。"
                        },
                        if (capabilities.systemClockAvailable) {
                            ReliabilityState.ENABLED
                        } else {
                            ReliabilityState.SYSTEM_LIMITED
                        },
                        if (capabilities.systemClockAvailable) {
                            ReliabilityAction.NONE
                        } else {
                            ReliabilityAction.OPEN_APP_DETAILS
                        },
                    ),
                )
            },
        )
    }

    private fun enabledState(enabled: Boolean): ReliabilityState =
        if (enabled) ReliabilityState.ENABLED else ReliabilityState.DISABLED
}
