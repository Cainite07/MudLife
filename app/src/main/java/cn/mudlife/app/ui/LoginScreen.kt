package cn.mudlife.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.mudlife.app.R
import cn.mudlife.app.ui.theme.paperBackground
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.ui.theme.LocalThemeReveal
import cn.mudlife.app.utils.HapticHelper

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: (String) -> Unit,
    onCampusCard: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = AppColors.isDark


    LaunchedEffect(viewModel.loginResult) {
        val result = viewModel.loginResult
        if (result != null && result.isSuccess) {
            onLoginSuccess(viewModel.phone)
            viewModel.resetResult()
        }
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The same restrained vector family as the device controls.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFF1E6C9))
                )
                Image(painterResource(R.mipmap.ic_launcher_foreground), "泥浆生活", modifier = Modifier.size(72.dp))
            }

            Text(
                "泥浆生活",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = AppColors.TextPrimary,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "趣智校园账号登录",
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(26.dp))

            // 🌟 核心悬浮充盈灵透晶体卡片 (浅色白玉高透 / 深色极光星幕) 🌟
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AppColors.Card
                ),
                border = BorderStroke(
                    1.dp,
                    AppColors.Border
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "欢迎回来",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        "饮水机与吹风机使用码",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                    )

                    // 🌟 登录方式物理果冻胶囊分段滑块 🌟
                    val isPwd = (viewModel.loginMode == "password")
                    val sliderProgress by animateFloatAsState(
                        targetValue = if (isPwd) 0f else 1f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                        label = "modeSlider"
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.Border)
                            .padding(3.dp)
                    ) {
                        val pillWidth = maxWidth / 2

                        // 高亮滑动药丸
                        Box(
                            modifier = Modifier
                                .width(pillWidth)
                                .fillMaxHeight()
                                .offset(x = pillWidth * sliderProgress)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.Accent)
                        )

                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        HapticHelper.tick(context)
                                        viewModel.loginMode = "password"
                                        viewModel.errorMessage = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "密码登录",
                                    fontSize = 13.sp,
                                    fontWeight = if (isPwd) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPwd) AppColors.OnAccent else AppColors.TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        HapticHelper.tick(context)
                                        viewModel.loginMode = "sms"
                                        viewModel.errorMessage = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "验证码登录",
                                    fontSize = 13.sp,
                                    fontWeight = if (!isPwd) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isPwd) AppColors.OnAccent else AppColors.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // 手机号输入框
                    OutlinedTextField(
                        value = viewModel.phone,
                        onValueChange = { viewModel.phone = it },
                        label = { Text("手机号") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Phone,
                                null,
                                tint = AppColors.Accent
                            )
                        },
                        trailingIcon = {
                            if (viewModel.phone.isNotEmpty()) {
                                IconButton(onClick = { viewModel.phone = "" }) {
                                    Text(
                                        "✕",
                                        color = AppColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppColors.Card,
                            unfocusedContainerColor = AppColors.Card,
                            focusedBorderColor = AppColors.Accent,
                            unfocusedBorderColor = AppColors.Border,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedLabelColor = AppColors.Accent,
                            unfocusedLabelColor = AppColors.TextSecondary
                        )
                    )

                    when (viewModel.loginMode) {
                        "password" -> {
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = viewModel.password,
                                onValueChange = { viewModel.password = it },
                                label = { Text("密码") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        null,
                                        tint = AppColors.Accent
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = AppColors.Card,
                                    unfocusedContainerColor = AppColors.Card,
                                    focusedBorderColor = AppColors.Accent,
                                    unfocusedBorderColor = AppColors.Border,
                                    focusedTextColor = AppColors.TextPrimary,
                                    unfocusedTextColor = AppColors.TextPrimary,
                                    focusedLabelColor = AppColors.Accent,
                                    unfocusedLabelColor = AppColors.TextSecondary
                                )
                            )

                            Spacer(Modifier.height(24.dp))

                            // 登录按钮 (蓝靛渐变流光)
                            Button(
                                onClick = {
                                    HapticHelper.heavyClick(context)
                                    viewModel.login()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = !viewModel.isLoading,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.Accent,
                                    disabledContainerColor = AppColors.Accent.copy(alpha = 0.5f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                if (viewModel.isLoading) {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("登 录", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(
                                "若忘记密码请在支付宝「趣智校园」中重置",
                                fontSize = 11.sp,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        "sms" -> {
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = viewModel.smsCode,
                                    onValueChange = { viewModel.smsCode = it },
                                    label = { Text("验证码") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Lock,
                                            null,
                                            tint = AppColors.Accent
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = AppColors.Card,
                                        unfocusedContainerColor = AppColors.Card,
                                        focusedBorderColor = AppColors.Accent,
                                        unfocusedBorderColor = AppColors.Border,
                                        focusedTextColor = AppColors.TextPrimary,
                                        unfocusedTextColor = AppColors.TextPrimary,
                                        focusedLabelColor = AppColors.Accent,
                                        unfocusedLabelColor = AppColors.TextSecondary
                                    )
                                )
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        HapticHelper.click(context)
                                        viewModel.sendSmsCode()
                                    },
                                    enabled = !viewModel.isLoading && viewModel.countdown == 0,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.ActiveBg,
                                        contentColor = AppColors.Accent,
                                        disabledContainerColor = if (isDark) Color(0x10FFFFFF) else Color(0x10000000),
                                        disabledContentColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF94A3B8)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        AppColors.Border
                                    ),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Text(
                                        if (viewModel.countdown > 0) "${viewModel.countdown}s" else "获取验证码",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    HapticHelper.heavyClick(context)
                                    viewModel.smsLogin()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = !viewModel.isLoading,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.Accent,
                                    disabledContainerColor = AppColors.Accent.copy(alpha = 0.5f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                if (viewModel.isLoading) {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("登 录", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                            }
                        }
                    }

                    viewModel.errorMessage?.let { msg ->
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x25EF4444),
                            border = BorderStroke(1.dp, Color(0x45EF4444))
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            CampusCardEntryCard(onClick = onCampusCard)
        }
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp)) {
            Icon(Icons.Outlined.ArrowBack, "返回")
        }
    }
}
