package cn.mudlife.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderVerificationTest {
    @Test fun active307IsNotTreatedAsFailureOrRemoved() {
        assertEquals(OrderVerification.ACTIVE, verifyOrder(BaseResponse(false, OrderStatus(orderNo = "order-a"), 307)))
    }
    @Test fun successfulEmptyStatusConfirmsStoppedDevice() {
        assertEquals(OrderVerification.ENDED, verifyOrder(BaseResponse<OrderStatus>(true, null)))
    }
    @Test fun networkOrLoginFailureMustNotEraseSavedOrder() {
        assertEquals(OrderVerification.UNKNOWN, verifyOrder(BaseResponse<OrderStatus>(false, null, 401)))
        assertEquals(OrderVerification.UNKNOWN, verifyOrder(BaseResponse<OrderStatus>(false, null, 500)))
    }
    @Test fun anotherPersonsOrderDoesNotRestoreMine() {
        assertEquals(OrderVerification.ENDED, verifyOrder(BaseResponse(false, OrderStatus("other", isOwner = false), 307)))
    }
    @Test fun normalActiveResponseCanRestoreOrder() {
        assertEquals(OrderVerification.ACTIVE, verifyOrder(BaseResponse(true, OrderStatus("mine"))))
    }
}
