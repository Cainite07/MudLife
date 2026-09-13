package com.hualala.linyu.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualala.linyu.R
import com.hualala.linyu.ui.theme.AppColors
import com.hualala.linyu.ui.theme.LocalThemeReveal
import com.hualala.linyu.utils.HapticHelper

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val isDark = AppColors.isDark

    var animated by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (animated) 1f else 0.88f, tween(450))

    LaunchedEffect(viewModel.loginResult) {
        val result = viewModel.loginResult
        if (result != null && result.isSuccess) {
            onLoginSuccess(viewModel.phone)
            viewModel.resetResult()
        }
    }

    LaunchedEffect(Unit) { animated = true }

    // 充盈灵透漫反射流光底板（浅色：天蓝珍珠白流光 / 深色：深空极光暗底）
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B101B),
                Color(0xFF0F172A),
                Color(0xFF080C14)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEBF3FC),
                Color(0xFFE2ECF8),
                Color(0xFFF5F8FC)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 顶部 Logo 徽标与光晕
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (isDark) Color(0x2038BDF8) else Color(0x250284C7))
                )
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "logo",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                )
            }

            Text(
                "泥浆生活",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "专注纯净校园供水与洗浴生活",
                fontSize = 12.sp,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(26.dp))

            // 🌟 核心悬浮充盈灵透晶体卡片 (浅色白玉高透 / 深色极光星幕) 🌟
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xD8151D2C) else Color(0xB8FFFFFF)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0x28FFFFFF) else Color(0xD8FFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "欢迎回来",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                    Text(
                        "请使用校园账号登录",
                        fontSize = 12.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B),
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
                            .background(if (isDark) Color(0x18FFFFFF) else Color(0x10000000))
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
                                .background(Color(0xFF2563EB))
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
                                    color = if (isPwd) Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B))
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
                                    color = if (!isPwd) Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B))
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
                                tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
                            )
                        },
                        trailingIcon = {
                            if (viewModel.phone.isNotEmpty()) {
                                IconButton(onClick = { viewModel.phone = "" }) {
                                    Text(
                                        "✕",
                                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
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
                            focusedContainerColor = if (isDark) Color(0x18FFFFFF) else Color(0x75FFFFFF),
                            unfocusedContainerColor = if (isDark) Color(0x10FFFFFF) else Color(0x55FFFFFF),
                            focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                            unfocusedBorderColor = if (isDark) Color(0x20FFFFFF) else Color(0x20000000),
                            focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                            unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                            focusedLabelColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                            unfocusedLabelColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
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
                                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (isDark) Color(0x18FFFFFF) else Color(0x75FFFFFF),
                                    unfocusedContainerColor = if (isDark) Color(0x10FFFFFF) else Color(0x55FFFFFF),
                                    focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                                    unfocusedBorderColor = if (isDark) Color(0x20FFFFFF) else Color(0x20000000),
                                    focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                    focusedLabelColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                                    unfocusedLabelColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
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
                                    containerColor = Color(0xFF2563EB),
                                    disabledContainerColor = Color(0xFF1D4ED8).copy(alpha = 0.5f)
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
                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B),
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
                                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = if (isDark) Color(0x18FFFFFF) else Color(0x75FFFFFF),
                                        unfocusedContainerColor = if (isDark) Color(0x10FFFFFF) else Color(0x55FFFFFF),
                                        focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                                        unfocusedBorderColor = if (isDark) Color(0x20FFFFFF) else Color(0x20000000),
                                        focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                        focusedLabelColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                                        unfocusedLabelColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
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
                                        containerColor = if (isDark) Color(0x2838BDF8) else Color(0x202563EB),
                                        contentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB),
                                        disabledContainerColor = if (isDark) Color(0x10FFFFFF) else Color(0x10000000),
                                        disabledContentColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF94A3B8)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) Color(0x3538BDF8) else Color(0x352563EB)
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
                                    containerColor = Color(0xFF2563EB),
                                    disabledContainerColor = Color(0xFF1D4ED8).copy(alpha = 0.5f)
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
        }
    }
}
