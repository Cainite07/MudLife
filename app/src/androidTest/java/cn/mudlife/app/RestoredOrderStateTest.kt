package cn.mudlife.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.mudlife.app.model.ActiveOrder
import cn.mudlife.app.model.BaseResponse
import cn.mudlife.app.model.OrderStatus
import cn.mudlife.app.ui.MainViewModel
import cn.mudlife.app.utils.PrefsHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

/** All order responses are local fixtures; this test cannot open a valve or call its API. */
@RunWith(AndroidJUnit4::class)
class RestoredOrderStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val cached = ActiveOrder("fixture-sn", "fixture-order", "测试设备", "fixture-mac", "🚰", 0.0, "43")

    @After fun removeOnlyFixtureOrder() {
        onMain {
            PrefsHelper.saveActiveOrders(PrefsHelper.getActiveOrders().filterNot {
                it.snCode == cached.snCode && it.orderNo == cached.orderNo
            })
        }
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            var done = false
            onMain { done = predicate() }
            if (done) return
            Thread.sleep(20)
        }
        fail("Local fixture did not settle")
    }

    @Test fun stoppedCacheNeverAppearsAsActiveAndIsRemovedFromDisk() {
        val response = CompletableDeferred<BaseResponse<OrderStatus>>()
        lateinit var model: MainViewModel
        onMain {
            PrefsHelper.init(instrumentation.targetContext)
            PrefsHelper.saveActiveOrders(listOf(cached))
            model = MainViewModel { response.await() }
            model.initManagers(instrumentation.targetContext)
            assertTrue(model.activeOrders.isEmpty())
            assertEquals(listOf(cached), model.unverifiedOrders.toList())
            assertFalse(model.isDeviceActive(cached.snCode))
        }
        response.complete(BaseResponse(true, null))
        waitUntil { !model.isVerifyingOrders }
        onMain {
            assertTrue(model.activeOrders.isEmpty())
            assertTrue(model.unverifiedOrders.isEmpty())
            assertTrue(PrefsHelper.getActiveOrders().isEmpty())
            model.logout()
        }
    }

    @Test fun realActive307IsOnlyShownAfterConfirmationAndNotRestoredAgainOnTabReturn() {
        val response = CompletableDeferred<BaseResponse<OrderStatus>>()
        lateinit var model: MainViewModel
        onMain {
            PrefsHelper.init(instrumentation.targetContext)
            PrefsHelper.saveActiveOrders(listOf(cached))
            model = MainViewModel { response.await() }
            model.initManagers(instrumentation.targetContext)
            assertTrue(model.activeOrders.isEmpty())
        }
        response.complete(BaseResponse(false, OrderStatus("fixture-order"), 307))
        waitUntil { !model.isVerifyingOrders }
        onMain {
            assertEquals(listOf(cached), model.activeOrders.toList())
            assertTrue(model.unverifiedOrders.isEmpty())
            assertTrue(model.isDeviceActive(cached.snCode))
            model.initManagers(instrumentation.targetContext)
            assertEquals(listOf(cached), model.activeOrders.toList())
            assertTrue(model.unverifiedOrders.isEmpty())
            model.logout()
            PrefsHelper.clearActiveOrders()
        }
    }

    @Test fun failedQueryRetainsUnverifiedRecordAndCanRetry() {
        var queryCount = 0
        lateinit var model: MainViewModel
        onMain {
            PrefsHelper.init(instrumentation.targetContext)
            PrefsHelper.saveActiveOrders(listOf(cached))
            model = MainViewModel {
                queryCount++
                if (queryCount == 1) BaseResponse(false, null, 401) else BaseResponse(true, null)
            }
            model.initManagers(instrumentation.targetContext)
        }
        waitUntil { !model.isVerifyingOrders }
        onMain {
            assertTrue(model.activeOrders.isEmpty())
            assertEquals(listOf(cached), model.unverifiedOrders.toList())
            assertEquals(listOf(cached), PrefsHelper.getActiveOrders())
            model.cleanInactiveOrders()
        }
        waitUntil { !model.isVerifyingOrders }
        onMain {
            assertEquals(2, queryCount)
            assertTrue(model.unverifiedOrders.isEmpty())
            assertTrue(PrefsHelper.getActiveOrders().isEmpty())
            model.logout()
        }
    }

    @Test fun deviceStopClearsPersistedOrderBeforeReceiptIsConfirmed() {
        onMain {
            PrefsHelper.init(instrumentation.targetContext)
            PrefsHelper.saveActiveOrders(listOf(cached))
            PrefsHelper.setStartedAt(cached.snCode, System.currentTimeMillis())
            val model = MainViewModel { BaseResponse(true, null) }
            model.activeOrders.add(cached)
            model.isShowering = true
            // Settlement is deliberately cancelled; synchronous stop cleanup must still happen.
            model.viewModelScope.cancel()
            MainViewModel::class.java.getDeclaredMethod("onAutoClose", String::class.java).apply {
                isAccessible = true
            }.invoke(model, cached.snCode)
            assertTrue(model.showAutoCloseDialog)
            assertTrue(model.activeOrders.isEmpty())
            assertTrue(PrefsHelper.getActiveOrders().isEmpty())
            assertEquals(0L, PrefsHelper.getStartedAt(cached.snCode))
            model.logout()
        }
    }
}
