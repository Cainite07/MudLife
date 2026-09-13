package com.hualala.linyu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualala.linyu.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun QzhqLoginDialog(
    initialPhone: String,
    onDismiss: () -> Unit,
    onPasswordSubmit: (String, String) -> Unit,
    onSmsSubmit: (String, String) -> Unit,
    onSendSms: (String, (Boolean) -> Unit) -> Unit
) {
    var loginMode by remember { mutableStateOf("password") } // "password" | "sms"
    var phone by remember { mutableStateOf(initialPhone) }
    var password by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }
    var isSendingSms by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = AppColors.SolidSurface,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = "登录江大后勤热水专区",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "用于同步全校各园区浴室水控机 8 位出水使用码（全校通用）",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 紧凑型并列双模切换按钮（小巧仿主页样式）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.Background)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                loginMode = "password"
                                errorMsg = ""
                            },
                        color = if (loginMode == "password") AppColors.Accent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "密码登录",
                                fontSize = 12.sp,
                                fontWeight = if (loginMode == "password") FontWeight.Bold else FontWeight.Normal,
                                color = if (loginMode == "password") Color.White else AppColors.TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                loginMode = "sms"
                                errorMsg = ""
                            },
                        color = if (loginMode == "sms") AppColors.Accent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "验证码登录",
                                fontSize = 12.sp,
                                fontWeight = if (loginMode == "sms") FontWeight.Bold else FontWeight.Normal,
                                color = if (loginMode == "sms") Color.White else AppColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 手机号输入框（双模通用）
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = "" },
                    label = { Text("手机号", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                // 切换内容区域
                if (loginMode == "password") {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMsg = ""
                        },
                        label = { Text("登录密码", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "👁️" else "🔒", fontSize = 15.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { smsCode = it; errorMsg = "" },
                            label = { Text("短信验证码", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (phone.length != 11) {
                                    errorMsg = "请输入正确的 11 位手机号"
                                    return@Button
                                }
                                isSendingSms = true
                                onSendSms(phone) { success ->
                                    isSendingSms = false
                                    if (success) {
                                        countdown = 60
                                        coroutineScope.launch {
                                            while (countdown > 0) {
                                                kotlinx.coroutines.delay(1000)
                                                countdown--
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = countdown == 0 && !isSendingSms,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Accent,
                                disabledContainerColor = AppColors.Accent.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isSendingSms) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    if (countdown > 0) "${countdown}s" else "获取验证码",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        color = AppColors.Danger,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "登录成功后自动保存在本地，出水码长效同步，平时无需重复登录。",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phone.length != 11) {
                        errorMsg = "请输入正确的 11 位手机号"
                    } else if (loginMode == "password") {
                        if (password.isEmpty()) {
                            errorMsg = "请输入密码"
                        } else {
                            onPasswordSubmit(phone, password)
                        }
                    } else {
                        if (smsCode.isEmpty()) {
                            errorMsg = "请输入验证码"
                        } else {
                            onSmsSubmit(phone, smsCode)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text("登录并同步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppColors.TextSecondary)
            }
        }
    )
}
