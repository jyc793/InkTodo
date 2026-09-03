package com.inktodo.app.feature.pomodoro.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inktodo.app.core.datastore.PomodoroRunState
import com.inktodo.app.core.focus.FullFocusStartResult
import com.inktodo.app.core.ui.components.InkBackdrop
import com.inktodo.app.core.ui.components.InkDestructiveButton
import com.inktodo.app.core.ui.components.InkGlassCard
import com.inktodo.app.core.ui.components.InkPrimaryButton
import com.inktodo.app.core.ui.components.InkSecondaryButton
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme

@Composable
internal fun FullFocusEntryAndOverlay(
    state: PomodoroUiState,
    viewModel: PomodoroViewModel,
) {
    var showPinSetup by remember { mutableStateOf(false) }
    var showDndChoice by remember { mutableStateOf(false) }
    var pendingPin by remember { mutableStateOf("") }
    var entryMessage by remember { mutableStateOf<String?>(null) }

    fun handleStartResult(result: FullFocusStartResult) {
        entryMessage = when (result) {
            FullFocusStartResult.STARTED -> null
            FullFocusStartResult.INVALID_PIN -> "请输入并确认六位数字密码。"
            FullFocusStartResult.DND_ACCESS_REQUIRED -> "请先在系统设置中允许墨待管理勿扰模式。"
            FullFocusStartResult.DND_ACTIVATION_FAILED -> "完全专注未能启动，请稍后重试。"
        }
        if (result == FullFocusStartResult.STARTED) {
            showDndChoice = false
            pendingPin = ""
        }
    }

    val policyAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (viewModel.hasDndPolicyAccess) {
            viewModel.startFullFocus(pendingPin, enableDnd = true, ::handleStartResult)
        } else {
            entryMessage = "尚未授予勿扰模式访问权限。你也可以选择“不启用勿扰”进入。"
        }
    }

    InkSecondaryButton(
        text = "开启完全专注",
        onClick = {
            entryMessage = null
            showPinSetup = true
        },
        enabled = state.runState == PomodoroRunState.IDLE,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("full_focus_entry"),
    )

    if (showPinSetup) {
        FullFocusPinSetupDialog(
            onDismiss = { showPinSetup = false },
            onConfirmed = { pin ->
                pendingPin = pin
                showPinSetup = false
                showDndChoice = true
            },
        )
    }

    if (showDndChoice) {
        AlertDialog(
            onDismissRequest = {
                showDndChoice = false
                pendingPin = ""
            },
            title = { Text("完全专注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm)) {
                    Text("是否在本次完全专注期间开启勿扰模式？退出或倒计时结束后，墨待会撤销自己启用的模式。")
                    entryMessage?.let {
                        Text(it, color = InkTheme.colors.cinnabar, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryMessage = null
                        if (viewModel.hasDndPolicyAccess) {
                            viewModel.startFullFocus(
                                pendingPin,
                                enableDnd = true,
                                ::handleStartResult,
                            )
                        } else {
                            runCatching {
                                policyAccessLauncher.launch(viewModel.dndPolicySettingsIntent())
                            }.onFailure {
                                entryMessage = "系统未提供可用的勿扰权限页面，请选择不启用勿扰。"
                            }
                        }
                    },
                ) { Text(if (viewModel.hasDndPolicyAccess) "开启勿扰并进入" else "去授权") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        entryMessage = null
                        viewModel.startFullFocus(
                            pendingPin,
                            enableDnd = false,
                            ::handleStartResult,
                        )
                    },
                ) { Text("不启用勿扰") }
            },
        )
    }

    if (state.fullFocus.enabled) {
        FullFocusOverlay(state, viewModel)
    }
}

@Composable
private fun FullFocusPinSetupDialog(
    onDismiss: () -> Unit,
    onConfirmed: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = pin.length == 6 && confirmation == pin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置完全专注密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm)) {
                Text("退出完全专注的第三次确认需要此六位数字密码。密码不会明文保存。")
                PinField("六位密码", pin) { pin = it }
                PinField("再次确认", confirmation) { confirmation = it }
                if (confirmation.isNotEmpty() && confirmation != pin) {
                    Text("两次密码不一致。", color = InkTheme.colors.cinnabar)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirmed(pin) },
            ) { Text("下一步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChanged(it.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FullFocusOverlay(
    state: PomodoroUiState,
    viewModel: PomodoroViewModel,
) {
    var controlsExpanded by remember { mutableStateOf(false) }
    var exitStage by remember { mutableIntStateOf(0) }
    var exitPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    val requestExit = {
        controlsExpanded = false
        exitStage = nextFullFocusExitStage(0)
    }

    Dialog(
        onDismissRequest = requestExit,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = requestExit)
        InkBackdrop(scene = InkScene.POMODORO) {
            Box(modifier = Modifier.fillMaxSize()) {
                TimerMirror(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.84f)
                        .aspectRatio(1f),
                    minimal = true,
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(InkTheme.spacing.md),
                    horizontalAlignment = Alignment.End,
                ) {
                    InkGlassCard(
                        modifier = Modifier
                            .width(InkTheme.spacing.touchTarget * 1.7f)
                            .testTag("full_focus_controls_toggle"),
                        onClick = { controlsExpanded = !controlsExpanded },
                    ) {
                        Text(
                            if (controlsExpanded) "收起" else "控制",
                            modifier = Modifier.padding(
                                horizontal = InkTheme.spacing.md,
                                vertical = InkTheme.spacing.sm,
                            ),
                            color = InkTheme.colors.ink,
                        )
                    }
                    AnimatedVisibility(
                        visible = controlsExpanded,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        InkGlassCard(
                            modifier = Modifier
                                .padding(top = InkTheme.spacing.sm)
                                .width(InkTheme.spacing.touchTarget * 4.2f),
                            emphasized = true,
                        ) {
                            Column(
                                modifier = Modifier.padding(InkTheme.spacing.md),
                                verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm),
                            ) {
                                when (state.runState) {
                                    PomodoroRunState.RUNNING -> InkPrimaryButton(
                                        text = "暂停",
                                        onClick = viewModel::pause,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    PomodoroRunState.PAUSED -> InkPrimaryButton(
                                        text = "继续",
                                        onClick = viewModel::resume,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    PomodoroRunState.IDLE -> InkPrimaryButton(
                                        text = "正在结束",
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                InkDestructiveButton(
                                    text = "申请退出",
                                    onClick = requestExit,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (exitStage) {
        1 -> ExitEncouragementDialog(
            title = "先别走，好吗？🥺",
            message = "你已经为自己留出了这段时间。再坚持一会儿，哪怕只完成眼前这一小步。",
            confirmText = "我仍要退出",
            onContinue = { exitStage = 0 },
            onExit = { exitStage = nextFullFocusExitStage(exitStage) },
        )
        2 -> ExitEncouragementDialog(
            title = "真的要离开吗？(｡•́︿•̀｡)",
            message = "锦鲤还在陪你走这一圈。把注意力带回来一次，也算今天很重要的胜利。",
            confirmText = "第二次确认退出",
            onContinue = { exitStage = 0 },
            onExit = { exitStage = nextFullFocusExitStage(exitStage) },
        )
        3 -> AlertDialog(
            onDismissRequest = {
                exitStage = 0
                exitPin = ""
                pinError = false
            },
            title = { Text("第三次确认") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InkTheme.spacing.sm)) {
                    Text("输入进入完全专注时设置的六位密码，才能退出。")
                    PinField("六位密码", exitPin) {
                        exitPin = it
                        pinError = false
                    }
                    if (pinError) {
                        Text("密码不正确，请再试一次。", color = InkTheme.colors.cinnabar)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exitPin.length == 6,
                    onClick = {
                        viewModel.exitFullFocus(exitPin) { verified ->
                            if (verified) {
                                exitStage = 0
                                exitPin = ""
                            } else {
                                pinError = true
                                exitPin = ""
                            }
                        }
                    },
                ) { Text("验证并退出") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        exitStage = 0
                        exitPin = ""
                        pinError = false
                    },
                ) { Text("继续专注") }
            },
        )
    }
}

internal fun nextFullFocusExitStage(current: Int): Int =
    (current + 1).coerceIn(1, 3)

@Composable
private fun ExitEncouragementDialog(
    title: String,
    message: String,
    confirmText: String,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onExit) { Text(confirmText, color = InkTheme.colors.cinnabar) }
        },
        dismissButton = {
            TextButton(onClick = onContinue) { Text("继续专注") }
        },
    )
}
