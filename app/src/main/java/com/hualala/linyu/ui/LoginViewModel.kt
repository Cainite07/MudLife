package com.hualala.linyu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualala.linyu.data.AuthRepository
import com.hualala.linyu.model.LoginData
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class LoginViewModel : ViewModel() {
    var phone by mutableStateOf("")
    var password by mutableStateOf("")
    var smsCode by mutableStateOf("")
    var loginMode by mutableStateOf("password")  // "password" | "sms"

    var isLoading by mutableStateOf(false)
        private set
    var loginResult by mutableStateOf<Result<LoginData>?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var countdown by mutableStateOf(0)
        private set

    fun login() {
        if (phone.isBlank() || password.isBlank()) {
            errorMessage = "请输入手机号和密码"
            return
        }
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = AuthRepository.login(phone.trim(), password)
            loginResult = result
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "登录失败"
                errorMessage = when {
                    msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true) -> "网络连接失败，请检查网络设置"
                    msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                        "连接超时，请检查网络后重试"
                    else -> msg
                }
            }
            isLoading = false
        }
    }

    fun sendSmsCode() {
        if (phone.isBlank()) {
            errorMessage = "请输入手机号"
            return
        }
        if (isLoading || countdown > 0) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = AuthRepository.sendSmsCode(phone.trim())
            if (result.isSuccess) {
                countdown = 60
                launch {
                    while (countdown > 0) {
                        delay(1000)
                        countdown--
                    }
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "验证码发送失败"
            }
            isLoading = false
        }
    }

    fun smsLogin() {
        if (phone.isBlank() || smsCode.isBlank()) {
            errorMessage = "请输入手机号和验证码"
            return
        }
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = AuthRepository.smsLogin(phone.trim(), smsCode.trim())
            loginResult = result
            if (result.isFailure) {
                errorMessage = result.exceptionOrNull()?.message ?: "登录失败"
            }
            isLoading = false
        }
    }

    fun resetResult() { loginResult = null }
}
