package cn.mudlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceLoginDialog(
    accountName: String,
    initialPhone: String,
    initialCountdown: Int = 0,
    onDismiss: () -> Unit,
    onPasswordSubmit: (String, String, (Boolean, String) -> Unit) -> Unit,
    onSmsSubmit: (String, String, (Boolean, String) -> Unit) -> Unit,
    onSendSms: (String, (Boolean, String) -> Unit) -> Unit
) {
    var smsMode by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf(initialPhone) }
    var password by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(initialCountdown) }
    var sending by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val validPhone = phone.length == 11 && phone.all { it in '0'..'9' }
    val fieldShape = RoundedCornerShape(12.dp)
    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = AppColors.Accent.copy(alpha = 0.14f),
        activeContentColor = AppColors.Accent,
        activeBorderColor = AppColors.Border,
        inactiveContainerColor = AppColors.SolidSurface,
        inactiveContentColor = AppColors.TextSecondary,
        inactiveBorderColor = AppColors.Border
    )

    LaunchedEffect(countdown > 0) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    AlertDialog(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(0.94f),
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        containerColor = AppColors.SolidSurface,
        tonalElevation = 0.dp,
        title = {
            Text("登录$accountName", fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                color = AppColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("密码登录", "验证码登录").forEachIndexed { index, label ->
                        val sms = index == 1
                        SegmentedButton(
                            selected = sms == smsMode,
                            onClick = { smsMode = sms; error = ""; showPassword = false },
                            shape = SegmentedButtonDefaults.itemShape(index, 2, fieldShape),
                            enabled = !submitting,
                            icon = {},
                            colors = segmentColors
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter { digit -> digit in '0'..'9' }.take(11)
                        error = ""
                    },
                    label = { Text("手机号") },
                    singleLine = true,
                    enabled = !submitting && !sending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!smsMode) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        label = { Text("密码") },
                        singleLine = true,
                        enabled = !submitting,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, enabled = !submitting) {
                                Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (showPassword) "隐藏密码" else "显示密码", tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp))
                            }
                        },
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { code -> smsCode = code.filter { it in '0'..'9' }; error = "" },
                            label = { Text("验证码") },
                            singleLine = true,
                            enabled = !submitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = fieldShape,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = validPhone && !sending && countdown == 0 && !submitting,
                            onClick = {
                                error = ""
                                sending = true
                                onSendSms(phone) { success, message ->
                                    sending = false
                                    if (success) countdown = 60 else error = message
                                }
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            when {
                                sending -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                countdown > 0 -> Text("${countdown} 秒后重发")
                                else -> Text("获取验证码")
                            }
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = AppColors.Danger, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && !sending,
                onClick = {
                    error = when {
                        !validPhone -> "请输入 11 位手机号"
                        smsMode && smsCode.isBlank() -> "请输入验证码"
                        !smsMode && password.isEmpty() -> "请输入密码"
                        else -> ""
                    }
                    if (error.isNotBlank()) return@Button
                    submitting = true
                    val result: (Boolean, String) -> Unit = { success, message ->
                        submitting = false
                        if (success) onDismiss() else error = message
                    }
                    if (smsMode) onSmsSubmit(phone, smsCode, result)
                    else onPasswordSubmit(phone, password, result)
                },
                shape = fieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent)
            ) { Text(if (submitting) "登录中…" else "登录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("返回", color = AppColors.TextSecondary)
            }
        }
    )
}
