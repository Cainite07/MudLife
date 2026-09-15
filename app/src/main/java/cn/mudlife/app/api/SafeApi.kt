package cn.mudlife.app.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import cn.mudlife.app.model.BaseResponse
import cn.mudlife.app.model.BillDetail
import cn.mudlife.app.model.BillItem
import cn.mudlife.app.model.CloseOrderResult
import cn.mudlife.app.model.DownRateResult
import cn.mudlife.app.model.DeviceInfo
import cn.mudlife.app.model.LoginData
import cn.mudlife.app.model.OrderStatus
import cn.mudlife.app.model.UseCodeData
import cn.mudlife.app.model.WalletData
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val gson = Gson()

private suspend fun Call<ResponseBody>.awaitString(): String {
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { this.cancel() }
        this.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        cont.resume(body.string())
                    } else {
                        cont.resumeWithException(Exception("Empty response body"))
                    }
                } else {
                    cont.resumeWithException(Exception("HTTP ${response.code()}: ${response.message()}"))
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                cont.resumeWithException(t)
            }
        })
    }
}

private fun <T> parse(json: String, dataClass: Class<T>): BaseResponse<T> {
    return try {
        val obj = JsonParser.parseString(json).asJsonObject
        val success = obj.get("success")?.let { if (it.isJsonNull) false else it.asBoolean } ?: false
        val errorCode = obj.get("errorCode")?.let { if (it.isJsonNull) 0 else it.asInt } ?: 0
        val errorMessage = obj.get("errorMessage")?.let { if (it.isJsonNull) null else it.asString }
        val msg = obj.get("msg")?.let { if (it.isJsonNull) null else it.asString }
        val dataElement = obj.get("data")
        val data: T? = if (dataElement != null && !dataElement.isJsonNull) {
            gson.fromJson(dataElement, dataClass)
        } else null
        BaseResponse(success, data, errorCode, errorMessage, msg)
    } catch (e: Exception) {
        BaseResponse(false, null, -1, "数据解析异常: ${e.message}", null)
    }
}

private fun <T> parseList(json: String, elementClass: Class<T>): BaseResponse<List<T>> {
    return try {
        val obj = JsonParser.parseString(json).asJsonObject
        val success = obj.get("success")?.let { if (it.isJsonNull) false else it.asBoolean } ?: false
        val errorCode = obj.get("errorCode")?.let { if (it.isJsonNull) 0 else it.asInt } ?: 0
        val errorMessage = obj.get("errorMessage")?.let { if (it.isJsonNull) null else it.asString }
        val msg = obj.get("msg")?.let { if (it.isJsonNull) null else it.asString }
        val dataElement = obj.get("data")
        val data: List<T>? = if (dataElement != null && !dataElement.isJsonNull && dataElement.isJsonArray) {
            val result = mutableListOf<T>()
            for (item in dataElement.asJsonArray) {
                result.add(gson.fromJson(item, elementClass))
            }
            result
        } else null
        BaseResponse(success, data, errorCode, errorMessage, msg)
    } catch (e: Exception) {
        BaseResponse(false, null, -1, "数据解析异常: ${e.message}", null)
    }
}

suspend fun QzxyService.loginSafe(
    telephone: String,
    password: String,
    phoneSystem: String = "android",
    type: Int = 0,
    version: String = "6.5.24"
): BaseResponse<LoginData> = parse(login(telephone, password, phoneSystem, type, version).awaitString(), LoginData::class.java)

suspend fun QzxyService.getWalletSafe(): BaseResponse<WalletData> =
    parse(getWallet().awaitString(), WalletData::class.java)

suspend fun QzxyService.getDeviceInfoSafe(mac: String): BaseResponse<DeviceInfo> =
    parse(getDeviceInfo(mac).awaitString(), DeviceInfo::class.java)

suspend fun QzxyService.downRateSafe(
    xfModel: Int = 0,
    snCode: String,
    auth: Map<String, String>
): BaseResponse<DownRateResult> = parse(downRate(xfModel, snCode, auth).awaitString(), DownRateResult::class.java)

suspend fun QzxyService.closeOrderSafe(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(closeOrder(snCode, orderNo, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.downRateResultSafe(
    snCode: String,
    auth: Map<String, String>
): BaseResponse<DownRateResult> = parse(downRateResult(snCode, auth).awaitString(), DownRateResult::class.java)

suspend fun QzxyService.closeOrderResultSafe(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): BaseResponse<CloseOrderResult> = parse(closeOrderResult(snCode, orderNo, auth).awaitString(), CloseOrderResult::class.java)

suspend fun QzxyService.consumeOrderResultSafe(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): BaseResponse<CloseOrderResult> = parse(consumeOrderResult(snCode, orderNo, auth).awaitString(), CloseOrderResult::class.java)

suspend fun QzxyService.queryUsingSafe(
    xfModel: Int = 0,
    snCode: String,
    auth: Map<String, String>
): BaseResponse<OrderStatus> = parse(queryUsing(xfModel, snCode, auth).awaitString(), OrderStatus::class.java)

data class UserProjectItem(
    val userId: Long = 0,
    val projectId: Long = 0,
    val accountId: Long = 0
)

suspend fun QzxyService.getUserProjectsSafe(): BaseResponse<List<UserProjectItem>> =
    parseList(getUserProjects().awaitString(), UserProjectItem::class.java)

suspend fun QzxyService.getBillListSafe(
    month: String,
    billRequestType: Int = 0,
    projectId: String? = null,
    accountId: String? = null
): BaseResponse<List<BillItem>> = parseList(getBillList(month, billRequestType, projectId, accountId).awaitString(), BillItem::class.java)

suspend fun QzxyService.getBillDetailSafe(
    orderId: String,
    consumeDate: String
): BaseResponse<BillDetail> = parse(getBillDetail(orderId, consumeDate).awaitString(), BillDetail::class.java)

suspend fun QzxyService.updateUseCodeStatusSafe(
    status: Int,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(updateUseCodeStatus(status, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.getUseCodeSafe(): BaseResponse<UseCodeData> =
    parse(getUseCode().awaitString(), UseCodeData::class.java)

suspend fun QzxyService.generateUseCodeSafe(
    auth: Map<String, String>
): BaseResponse<UseCodeData> = parse(generateUseCode(auth).awaitString(), UseCodeData::class.java)

suspend fun QzxyService.getVerificationCodeSafe(telephone: String): BaseResponse<Unit> {
    val secret = cn.mudlife.app.utils.SignUtils.sign(telephone)
    return parse(this.getVerificationCode(telephone, secret = secret).awaitString(), Unit::class.java)
}

suspend fun QzxyService.registerAndLoginSafe(telephone: String, smsCode: String): BaseResponse<LoginData> =
    parse(registerAndLogin(telephone, smsCode).awaitString(), LoginData::class.java)
