package cn.mudlife.app.model

/** A restored record is only a candidate until the server confirms it. */
enum class OrderVerification { ACTIVE, ENDED, UNKNOWN }

fun verifyOrder(response: BaseResponse<OrderStatus>): OrderVerification = when {
    response.errorCode == 307 -> if (response.data?.isOwner == false) OrderVerification.ENDED else OrderVerification.ACTIVE
    response.success && !response.data?.orderNo.isNullOrEmpty() ->
        if (response.data?.isOwner == false) OrderVerification.ENDED else OrderVerification.ACTIVE
    response.success -> OrderVerification.ENDED
    else -> OrderVerification.UNKNOWN
}
