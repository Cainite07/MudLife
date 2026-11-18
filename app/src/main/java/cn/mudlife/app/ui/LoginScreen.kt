package cn.mudlife.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.mudlife.app.utils.PrefsHelper

@Composable
internal fun CampusServiceLoginDialog(
    onDismiss: () -> Unit, onLoginSuccess: (String) -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    ServiceLoginDialog(
        accountName = "趣智校园",
        initialPhone = PrefsHelper.telephone,
        initialCountdown = viewModel.smsWaitSeconds(),
        onDismiss = onDismiss,
        onPasswordSubmit = { phone, password, finish ->
            viewModel.login(phone, password) { ok, message ->
                if (ok) onLoginSuccess(phone)
                finish(ok, message)
            }
        },
        onSmsSubmit = { phone, code, finish ->
            viewModel.smsLogin(phone, code) { ok, message ->
                if (ok) onLoginSuccess(phone)
                finish(ok, message)
            }
        },
        onSendSms = viewModel::sendSmsCode
    )
}
