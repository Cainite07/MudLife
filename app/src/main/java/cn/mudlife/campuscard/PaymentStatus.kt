package cn.mudlife.campuscard

enum class PaymentState { PAID, AWAITING, TIMED_OUT, UNKNOWN }
object PaymentPolicy {
    fun state(status: Int, expectedFen: Long, returnedYuan: String): PaymentState {
        if (expectedFen <= 0 || Money.parseYuan(returnedYuan) != expectedFen) return PaymentState.UNKNOWN
        return when (status) { 1 -> PaymentState.PAID; 0 -> PaymentState.AWAITING; 2 -> PaymentState.TIMED_OUT; else -> PaymentState.UNKNOWN }
    }
    fun message(state: PaymentState) = when (state) {
        PaymentState.PAID -> "学校确认支付成功"
        PaymentState.AWAITING -> "尚未确认支付，原订单已保留"
        PaymentState.TIMED_OUT -> "订单已超时，付款状态仍需核对"
        PaymentState.UNKNOWN -> "暂未查到可确认的支付结果，原订单已保留"
    }
}
class PendingPayment(val checkout: String, val orderId: String, val kind: String, val fen: Long) {
    fun matches(session: SessionStore) = checkout == session.pendingCheckout && orderId == session.pendingOrderId && kind == session.pendingKind && fen == session.pendingFen
    companion object {
        fun capture(session: SessionStore): PendingPayment? {
            val checkout = session.pendingCheckout
            if (checkout.isBlank() || session.pendingFen <= 0) return null
            if (session.pendingKind == "electric") {
                if (!session.pendingOrderId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) return null
            } else if (Navigation.checkoutReference(checkout) == null) return null
            return PendingPayment(checkout, session.pendingOrderId, session.pendingKind, session.pendingFen)
        }
    }
}
suspend fun SchoolApi.pendingPaymentStatus(payment: PendingPayment): PaymentState {
    val reference = if (payment.kind != "electric") Navigation.checkoutReference(payment.checkout) else null
    val id = reference?.orderId ?: payment.orderId
    if (!id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) return PaymentState.UNKNOWN
    val result = try { get("/charge/order/personal_data", mapOf("orderid" to id), bearerOverride = reference?.token) }
        catch (e: LoginExpired) { if (reference != null) throw SchoolFailure("原订单查询凭证已失效，订单记录已保留") else throw e }
    val matches = result.optJSONArray("orderList").objects().filter { it.clean("orderid") == id }
    if (matches.size != 1) return PaymentState.UNKNOWN
    val order = matches.single()
    return PaymentPolicy.state(order.optInt("status", -1), payment.fen, order.clean("tranamt"))
}
