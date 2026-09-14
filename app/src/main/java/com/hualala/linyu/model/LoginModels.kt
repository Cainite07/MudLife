package com.hualala.linyu.model

import com.google.gson.annotations.SerializedName

data class BaseResponse<T>(
    val success: Boolean,
    val data: T?,
    @SerializedName("errorCode") val errorCode: Int = 0,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    val msg: String? = null
) {
    val displayMessage: String?
        get() = errorMessage ?: msg
}

data class LoginData(
    val userId: Long,
    val loginCode: String,
    val userAccount: UserAccount
)

data class UserAccount(
    val accountId: Long,
    val name: String,
    val projectId: Long,
    val accountRealMoney: Double,
    val idCardNumber: String? = null
)

data class WalletData(
    val accountRealMoney: Double,
    val accountGivenMoney: Double,
    val money: String
)

data class OrderStatus(
    val orderNo: String? = null,
    val state: Int? = null,
    val snCode: String? = null,
    val isOwner: Boolean = true
)

data class BillItem(
    val billType: Int = 0,
    val rechargeBillDTO: BillDTO? = null,
    val consumeBillDTO: BillDTO? = null,
    val laundryBillDTO: BillDTO? = null,
    val refundBillDTO: BillDTO? = null,
    val monthCardBillDTO: BillDTO? = null,
    val sortTimestamp: Long? = null
) {
    /**
     * 统一获取实际有效的账单对象：
     * 优先 consumeBillDTO（水表），
     * 其次 laundryBillDTO（吹风机、洗衣机等），
     * 再次 rechargeBillDTO、refundBillDTO、monthCardBillDTO。
     */
    val actualBill: BillDTO get() = consumeBillDTO
        ?: laundryBillDTO
        ?: rechargeBillDTO
        ?: refundBillDTO
        ?: monthCardBillDTO
        ?: BillDTO()

    /** 兼容旧版代码调用，杜绝因 consumeBillDTO=null 导致的 NPE 崩溃 */
    val safeConsumeBillDTO: BillDTO get() = actualBill
}

data class BillDTO(
    val orderId: String? = null,
    val billId: Long? = null,
    val orderNo: String? = null,
    val dealDate: String? = null,
    val consumeDate: String? = null,
    val createTime: String? = null,
    val uploadDate: String? = null,
    val consumeMoney: String? = null,
    val thirdTradeMoney: String? = null,
    val dealMoney: String? = null,
    val description: String? = null,
    val deviceDescription: String? = null,
    val deviceTypeName: String? = null,
    val deductTypeName: String? = null,
    val deductType: Int? = null,
    val accountId: Long? = null,
    val deviceId: Long? = null,
    val telephone: String? = null,
    val beforeMoney: String? = null,
    val afterMoney: String? = null
) {
    val safeOrderId: String get() = orderId ?: billId?.toString() ?: orderNo ?: "0"

    val safeConsumeDate: String get() = when {
        !consumeDate.isNullOrBlank() -> consumeDate
        !dealDate.isNullOrBlank() -> dealDate
        !createTime.isNullOrBlank() -> createTime
        else -> ""
    }

    val safeConsumeMoney: String get() {
        val cm = consumeMoney?.toDoubleOrNull() ?: 0.0
        val tm = thirdTradeMoney?.toDoubleOrNull() ?: 0.0
        // 优先使用 consumeMoney（实扣金额），仅当为 0 时降级到 thirdTradeMoney（第三方代扣）
        val amount = if (cm > 0.0) cm else tm
        return if (amount > 0.0) {
            String.format(java.util.Locale.US, "%.2f", amount)
        } else {
            consumeMoney ?: "0.00"
        }
    }

    /** 设备名：龙川北苑 3号楼南 320房 */
    val displayDesc: String get() {
        val raw = description ?: deviceDescription ?: deviceTypeName ?: "校园设备"
        val name = raw.substringAfter(":")
        return if (name.isNotEmpty()) DeviceInfo.formatDeviceName(name) else raw
    }

    /** 设备类型标签：直饮水机 / 共享吹风机 / 洗手台热水器 / 宿舍浴室热水器 */
    val deviceTypeLabel: String get() {
        val type = deviceTypeName ?: ""
        val desc = (description ?: "") + (deviceDescription ?: "")
        return when {
            type.contains("吹风") || desc.contains("吹风") || desc.contains("电吹风") || desc.contains("风筒") || desc.contains("风机") -> "💨 共享吹风机"
            type.contains("饮水") || desc.contains("饮水") || desc.contains("冷水") || desc.contains("开水") || desc.contains("直饮") -> "🚰 直饮水机"
            type.contains("洗手") || desc.contains("洗手台", ignoreCase = true) -> "🪥 洗手台热水器"
            type.contains("洗衣") || desc.contains("洗衣") -> "🧺 共享洗衣机"
            else -> "🚿 宿舍浴室热水器"
        }
    }
}

data class UseCodeData(
    val useCode: String = "",
    val useCodeStatus: Int = 0,
    val useCodeRandom: String = "",
    val resetAvailability: Int = 0,
    val resetAvailabilityWarMark: String? = null,
    val remainTimes: Int = 20
)

data class BillDetail(
    val orderId: String? = null,
    val consumeDate: String? = null,
    val consumeMoney: Double = 0.0,
    val description: String? = null,
    val deviceSnCode: String? = null,
    val orderNo: String? = null,
    val preDeductMoney: Double = 0.0
)

/**
 * 开阀结果查询 (/order/tcpDevice/query/downRateResult)
 *
 * 用于确认 downRate 开阀是否真正成功，同时可携带 autoDisConTime（自动关停秒数）。
 */
data class DownRateResult(
    /** 订单号 */
    val orderNo: String? = null,
    /** 自动关停时间（秒），如 600 = 10 分钟 */
    val autoDisConTime: Int? = null,
    /** 状态码，0 通常表示开阀成功 */
    val state: Int? = null,
    val result: Int? = null,
    /** 预扣金额 */
    val preDeductMoney: Double? = null,
    val preDeductMoneySend: Double? = null,
    /** 费率 */
    val rate: Double? = null,
    /** 设备序列号 */
    val snCode: String? = null
)

/**
 * 关阀结果查询 (/order/tcpDevice/closeOrder/result/query)
 *
 * 用于确认 closeOrder 是否真正执行成功。服务器对成功/失败的字段
 * 命名可能因学校而异，故用 @SerializedName 做多字段容错。
 */
data class CloseOrderResult(
    /** 订单号 */
    val orderNo: String? = null,
    /** 订单状态：1=使用中，0=已关闭（部分服务器用 state） */
    val state: Int? = null,
    /** 订单状态（部分服务器用 status） */
    val status: Int? = null,
    /** 操作结果码，0 通常表示成功 */
    val result: Int? = null,
    /** 最终消费金额（元，数字形式） */
    val consumeMoney: Double? = null,
    /** 最终消费金额（元，字符串形式，部分学校返回） */
    @SerializedName("consumeMoneyStr") val consumeMoneyStr: String? = null,
    /** 结算时间 */
    val consumeTime: String? = null,
    /** 设备序列号 */
    val deviceSnCode: String? = null
)

/**
 * 江大后勤热水专区账单数据结构 (/combination/wallet/billList)
 */
data class QzhqWalletBillItem(
    val dealDate: String? = null,
    val time: String? = null,
    val upMoney: Double? = null,
    val upLeadMoney: Double? = null,
    val xfMoney: Double? = null,
    val dealMoney: Double? = null,
    val afterMoney: Double? = null,
    val beforeMoney: Double? = null,
    val consumeType: String? = null,
    val dealMark: String? = null,
    val opName: String? = null,
    val description: String? = null,
    val areaName: String? = null,
    val comsumexfMode: Int? = null,
    val accountId: Long? = null,
    val upState: Int? = null
)
