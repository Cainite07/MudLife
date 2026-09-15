package cn.mudlife.app.data

import cn.mudlife.app.api.NetworkModule
import cn.mudlife.app.api.getUserProjectsSafe
import cn.mudlife.app.api.getVerificationCodeSafe
import cn.mudlife.app.api.loginSafe
import cn.mudlife.app.api.registerAndLoginSafe
import cn.mudlife.app.model.LoginData
import cn.mudlife.app.utils.MD5Utils
import cn.mudlife.app.utils.PrefsHelper

object AuthRepository {
    private suspend fun syncUserProjects() {
        try {
            val pResp = NetworkModule.apiService.getUserProjectsSafe()
            if (pResp.success && !pResp.data.isNullOrEmpty()) {
                val map = pResp.data.associate { it.projectId.toString() to it.accountId.toString() }
                NetworkModule.setProjectAccounts(map)
                PrefsHelper.saveProjectAccounts(map)
                cn.mudlife.app.utils.AppLogger.i("Auth", "已成功同步多项目账户映射: $map")
            }
        } catch (e: Exception) {
            cn.mudlife.app.utils.AppLogger.w("Auth", "拉取多项目列表失败: ${e.message}")
        }
    }

    suspend fun login(phone: String, passwordRaw: String): Result<LoginData> {
        return try {
            val encryptedPassword = MD5Utils.encryptPassword(passwordRaw)
            val response = NetworkModule.apiService.loginSafe(
                telephone = phone,
                password = encryptedPassword
            )
            if (response.success && response.data != null) {
                val data = response.data
                NetworkModule.updateAuth(
                    loginCode = data.loginCode,
                    userId = data.userId.toString(),
                    accountId = data.userAccount.accountId.toString(),
                    projectId = data.userAccount.projectId.toString(),
                    telephone = phone
                )
                // 持久化
                val sno = data.userAccount.idCardNumber?.takeIf { it.isNotBlank() }
                PrefsHelper.saveAuth(
                    data.loginCode, data.userId.toString(),
                    data.userAccount.accountId.toString(),
                    data.userAccount.projectId.toString(), phone,
                    data.userAccount.name,
                    sno
                )
                syncUserProjects()
                Result.success(data)
            } else {
                Result.failure(Exception(response.displayMessage ?: "登录失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSmsCode(phone: String): Result<Unit> {
        return try {
            val resp = NetworkModule.apiService.getVerificationCodeSafe(phone)
            if (resp.success) Result.success(Unit)
            else Result.failure(Exception(resp.displayMessage ?: "发送失败"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun smsLogin(phone: String, smsCode: String): Result<LoginData> {
        return try {
            val response = NetworkModule.apiService.registerAndLoginSafe(phone, smsCode)
            if (response.success && response.data != null) {
                val data = response.data
                val sno = data.userAccount.idCardNumber?.takeIf { it.isNotBlank() }
                NetworkModule.updateAuth(data.loginCode, data.userId.toString(),
                    data.userAccount.accountId.toString(), data.userAccount.projectId.toString(), phone)
                PrefsHelper.saveAuth(data.loginCode, data.userId.toString(),
                    data.userAccount.accountId.toString(), data.userAccount.projectId.toString(), phone, data.userAccount.name, sno)
                syncUserProjects()
                Result.success(data)
            } else {
                Result.failure(Exception(response.displayMessage ?: "登录失败"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}
