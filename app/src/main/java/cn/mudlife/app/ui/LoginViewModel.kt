package cn.mudlife.app.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.mudlife.app.data.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private var busy = false
    private var smsAvailableAt = 0L

    fun smsWaitSeconds(): Int =
        ((smsAvailableAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L).div(1000L).toInt()

    fun login(phone: String, password: String, result: (Boolean, String) -> Unit) =
        submit("登录失败", result) { AuthRepository.login(phone, password) }

    fun smsLogin(phone: String, code: String, result: (Boolean, String) -> Unit) =
        submit("登录失败", result) { AuthRepository.smsLogin(phone, code) }

    fun sendSmsCode(phone: String, result: (Boolean, String) -> Unit) {
        if (smsWaitSeconds() > 0) {
            result(false, "请等倒计时结束后重试")
            return
        }
        submit("验证码发送失败", result) {
            AuthRepository.sendSmsCode(phone).onSuccess {
                smsAvailableAt = SystemClock.elapsedRealtime() + 60_000L
            }
        }
    }

    private fun submit(fallback: String, onResult: (Boolean, String) -> Unit, request: suspend () -> Result<*>) {
        if (busy) {
            onResult(false, "请求正在处理，请稍候")
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val response = request()
                val message = response.exceptionOrNull()?.message.orEmpty()
                val display = when {
                    response.isSuccess -> ""
                    message.contains("Unable to resolve host", ignoreCase = true) ||
                        message.contains("No address associated", ignoreCase = true) ||
                        message.contains("Network is unreachable", ignoreCase = true) -> "网络连接失败，请检查网络"
                    message.contains("timeout", ignoreCase = true) || message.contains("timed out", ignoreCase = true) ->
                        "连接超时，请重试"
                    else -> message.ifBlank { fallback }
                }
                onResult(response.isSuccess, display)
            } finally {
                busy = false
            }
        }
    }
}
