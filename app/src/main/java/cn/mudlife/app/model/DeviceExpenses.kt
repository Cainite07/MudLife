package cn.mudlife.app.model

import cn.mudlife.campuscard.Bill
import cn.mudlife.campuscard.BillLedger
import java.time.LocalDateTime

data class DeviceExpense(val order: String, val bill: Bill)
data class StatementRow(val key: String, val bills: List<Bill>, val drinking: Boolean = false) {
    val day: String get() = bills.first().time.take(10)
}

/** Only independent drinking/dryer payments supplement the school's authoritative ledger. */
object DeviceExpenses {
    private val dryer = Regex("吹风|电吹风|风筒|风机")
    private val water = Regex("饮水|直饮|冷水|开水|水机")
    private val campus = Regex("校园卡|一卡通|学校卡")
    private val independent = Regex("支付宝|微信|余额|钱包|第三方|代扣|免密")

    fun parse(item: BillItem): DeviceExpense? {
        if (item.rechargeBillDTO != null || item.refundBillDTO != null || item.monthCardBillDTO != null) return null
        val d = item.consumeBillDTO ?: item.laundryBillDTO ?: return null
        val description = listOfNotNull(d.deviceTypeName, d.description, d.deviceDescription).joinToString(" ")
        val title = when { dryer.containsMatchIn(description) -> "吹风机消费"; water.containsMatchIn(description) -> "饮水消费"; else -> return null }
        if (campus.containsMatchIn(d.deductTypeName.orEmpty())) return null
        val thirdParty = d.thirdTradeMoney?.toBigDecimalOrNull()?.signum() == 1
        if (!thirdParty && !independent.containsMatchIn(d.deductTypeName.orEmpty())) return null
        val time = UnifiedLedger.normalizeTime(d.safeConsumeDate)
        if (runCatching { LocalDateTime.parse(time.replace(' ', 'T')) }.isFailure) return null
        val values = listOf(d.consumeMoney, d.thirdTradeMoney, d.dealMoney).mapNotNull { it?.toBigDecimalOrNull() }
        val amount = values.firstOrNull { it.signum() != 0 } ?: values.firstOrNull()
        if (amount != null && amount.signum() <= 0) return null
        val fen = runCatching { amount?.movePointRight(2)?.longValueExact() }.getOrNull()
        val order = d.safeOrderId.takeUnless { it == "0" }.orEmpty()
        val id = "device:" + order.ifBlank { "$time:${d.deviceId}:${amount}:${description}" }
        return DeviceExpense(order, Bill(id, title, time, fen, "1", "消费", d.displayDesc,
            d.deductTypeName.orEmpty(), null, "2", listOf(d.displayDesc, d.deductTypeName.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")))
    }

    fun merge(school: List<Bill>, devices: List<DeviceExpense>): List<Bill> {
        val schoolOrders = school.map { it.id }.filter { it.isNotBlank() }.toSet()
        return (school + devices.filter { it.order.isBlank() || it.order !in schoolOrders }.map { it.bill })
            .distinctBy(BillLedger::key).sortedByDescending { it.time }
    }

    fun rows(records: List<Bill>): List<StatementRow> = records.sortedByDescending { it.time }.groupBy { it.time.take(10) }.flatMap { (day, bills) ->
        val (drinking, normal) = bills.partition { CampusBillLedger.category(it) == CampusBillCategory.WATER }
        normal.map { StatementRow(BillLedger.key(it), listOf(it)) } +
            if (drinking.isEmpty()) emptyList() else listOf(StatementRow("water:$day", drinking, true))
    }
}
