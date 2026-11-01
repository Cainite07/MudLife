package cn.mudlife.app.model

import cn.mudlife.campuscard.Bill
import org.junit.Assert.*
import org.junit.Test

class DeviceExpensesTest {
    private fun dto(name: String = "共享吹风机", payment: String = "支付宝免密", amount: String? = "0.60", id: String = "dryer") =
        BillDTO(orderId = id, consumeDate = "2026-10-29 22:50:00", consumeMoney = amount, deviceTypeName = name, deductTypeName = payment)
    private fun school(id: String, title: String, time: String, fen: Long? = 50) =
        Bill(id, title, "2026-10-29 $time", fen, "1", "", title, "", null)

    @Test fun codeStartedDryerUsesLaundryPayloadAndExactFen() {
        val parsed = DeviceExpenses.parse(BillItem(laundryBillDTO = dto()))!!
        assertEquals("吹风机消费", parsed.bill.title)
        assertEquals(60L, parsed.bill.fen)
        assertEquals(CampusBillCategory.DRYER, CampusBillLedger.category(parsed.bill))
        assertEquals("支付宝免密", parsed.bill.payment)
    }

    @Test fun topupsRefundsCampusDebitsAndUnrelatedDevicesAreExcluded() {
        assertNull(DeviceExpenses.parse(BillItem(rechargeBillDTO = dto())))
        assertNull(DeviceExpenses.parse(BillItem(refundBillDTO = dto())))
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto(payment = "校园卡扣费"))))
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto(name = "共享洗衣机"))))
        assertNull(DeviceExpenses.parse(BillItem(consumeBillDTO = dto(name = "浴室热水器"))))
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto(payment = ""))))
    }

    @Test fun thirdPartyAmountIdentifiesIndependentPaymentButNeverOverridesCampusLabel() {
        assertEquals(60L, DeviceExpenses.parse(BillItem(laundryBillDTO = dto(payment = "", amount = "0").copy(thirdTradeMoney = "0.60")))!!.bill.fen)
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto(payment = "校园卡").copy(thirdTradeMoney = "0.60"))))
    }

    @Test fun schoolOrderWinsAndSeparatePaymentsAtSameTimeAndAmountRemainSeparate() {
        val device = DeviceExpenses.parse(BillItem(laundryBillDTO = dto()))!!
        val official = device.bill.copy(id = "dryer")
        assertEquals(listOf(official), DeviceExpenses.merge(listOf(official), listOf(device, device)))
        val second = DeviceExpenses.parse(BillItem(laundryBillDTO = dto(id = "other")))!!
        assertEquals(2, DeviceExpenses.merge(emptyList(), listOf(device, device, second)).size)
    }

    @Test fun allDailyWaterIsOneFinalRowRegardlessOfTimeAndKeepsIndividualDetails() {
        val water1 = school("water1", "直饮水", "23:00:00", 2)
        val water2 = school("water2", "饮水消费", "10:00:00", 3)
        val food = school("food", "第一食堂", "12:00:00", 1200)
        val dryer = school("dryer", "吹风机消费", "22:50:00", 60)
        val yesterday = water1.copy(id = "yesterday", time = "2026-10-28 23:50:00")
        val rows = DeviceExpenses.rows(listOf(water2, food, yesterday, water1, dryer))
        assertEquals(listOf("dryer", "food"), rows.take(2).map { it.bills.single().id })
        assertTrue(rows[2].drinking)
        assertEquals(listOf(water1, water2), rows[2].bills)
        assertEquals(5L, CampusBillLedger.expense(rows[2].bills))
        assertEquals("2026-10-28", rows[3].day)
        assertEquals(1267L, CampusBillLedger.expense(listOf(water1, water2, food, dryer, yesterday)))
    }

    @Test fun invalidDatesAndNonExpenseAmountsAreNotFabricatedAsPayments() {
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto().copy(consumeDate = ""))))
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto(amount = "0"))))
        assertNull(DeviceExpenses.parse(BillItem(laundryBillDTO = dto(amount = "-0.60"))))
        val unknown = DeviceExpenses.parse(BillItem(laundryBillDTO = dto(amount = null)))!!
        assertNull(unknown.bill.fen)
        assertNull(CampusBillLedger.expense(listOf(unknown.bill)))
    }
}
