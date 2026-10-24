package cn.mudlife.app.model

import cn.mudlife.campuscard.Bill
import cn.mudlife.campuscard.BillLedger
import java.time.YearMonth

data class LedgerEntry(val key: String, val title: String, val time: String, val fen: Long?, val income: Boolean?,
    val source: String, val order: String, val description: String, val campusDebit: Boolean = false, val bath: Boolean = false)
data class MergedLedger(val entries: List<LedgerEntry>, val usesSchoolDebits: Boolean)

/** School statements own campus debits; device-wallet statements own independent wallet movements. */
object UnifiedLedger {
    fun school(b: Bill) = LedgerEntry("school:${BillLedger.key(b)}", b.title, normalizeTime(b.time), b.fen?.let { if (it == Long.MIN_VALUE) null else kotlin.math.abs(it) },
        when (BillLedger.direction(b)) { "1" -> true; "2" -> false; else -> null }, "school", b.id, b.description.ifBlank { b.merchant },
        bath = b.title == "洗浴消费")

    fun life(b: BillItem): LedgerEntry {
        val d = b.actualBill
        val source = if (b.source == "qzhq") "qzhq" else "qzxy"
        val title = when {
            b.rechargeBillDTO != null -> "趣智充值"
            b.refundBillDTO != null -> "设备退款"
            d.deviceTypeLabel.contains("吹风") -> "吹风机"
            d.deviceTypeLabel.contains("直饮") -> "直饮水"
            d.deviceTypeLabel.contains("洗衣") -> "洗衣消费"
            else -> "洗浴消费"
        }
        val values = listOf(d.consumeMoney, d.thirdTradeMoney, d.dealMoney).mapNotNull { it?.toBigDecimalOrNull() }
        val amount = values.firstOrNull { it.signum() != 0 } ?: values.firstOrNull()
        val money = runCatching { amount?.movePointRight(2)?.longValueExact()?.let { if (it == Long.MIN_VALUE) null else kotlin.math.abs(it) } }.getOrNull()
        return LedgerEntry("$source:${d.safeOrderId}:${d.safeConsumeDate}:${d.safeConsumeMoney}", title, normalizeTime(d.safeConsumeDate), money,
            b.rechargeBillDTO != null || b.refundBillDTO != null, source, d.safeOrderId.takeUnless { it == "0" }.orEmpty(), d.displayDesc,
            source == "qzhq" || d.deductTypeName.orEmpty().contains("校园卡"), title == "洗浴消费")
    }

    fun merge(school: List<LedgerEntry>, life: List<LedgerEntry>, schoolComplete: Boolean): MergedLedger {
        // Logistics records describe campus debits, not a second wallet. Once the full school
        // statement is available, use that ledger regardless of device/server posting delays.
        // Keep the original logistics rows available in the separate source view.
        val independent = if (schoolComplete) life.filterNot { it.campusDebit } else life
        return MergedLedger((school + independent).distinctBy { it.key }.sortedByDescending { it.time },
            schoolComplete && life.any { it.campusDebit })
    }
    fun normalizeTime(time: String) = time.trim().replace('T', ' ').replace('/', '-').take(19)
    fun select(entries: List<LedgerEntry>, month: YearMonth?, source: String) = entries.filter {
        (month == null || it.time.startsWith(month.toString())) && when (source) { "school" -> it.source == "school"; "life" -> it.source != "school"; else -> true }
    }
    fun sum(entries: List<LedgerEntry>, income: Boolean): Long? = runCatching {
        if (entries.any { it.income == null || it.fen == null }) return null
        entries.filter { it.income == income }.fold(0L) { sum, row -> Math.addExact(sum, row.fen!!) }
    }.getOrNull()
}

enum class CampusBillCategory(val label: String) {
    ALL("全部"), FOOD("饮食"), BATH("洗澡"), RECHARGE("充值"), OTHER("其他")
}

/** The everyday statement belongs to the campus card, without device-wallet transfers. */
object CampusBillLedger {
    private val drinking = Regex("直饮|饮水|喝水|水机|趣智充值")
    private val bathing = Regex("洗浴|洗澡|淋浴|浴室|热水")
    private val food = Regex("食堂|餐厅|餐饮|饮食|饭|面馆|小吃|早餐|午餐|晚餐|快餐|美食|食府|食坊|咖啡|奶茶|烘焙|蛋糕")

    fun category(bill: Bill): CampusBillCategory {
        val text = listOf(bill.title, bill.merchant, bill.description).joinToString(" ")
        return when {
            bill.type == "2" || (BillLedger.direction(bill) == "1" && text.contains("充值")) -> CampusBillCategory.RECHARGE
            bathing.containsMatchIn(text) -> CampusBillCategory.BATH
            food.containsMatchIn(text) -> CampusBillCategory.FOOD
            else -> CampusBillCategory.OTHER
        }
    }

    fun select(records: List<Bill>, month: YearMonth?, category: CampusBillCategory = CampusBillCategory.ALL): List<Bill> =
        records.filter { bill ->
            (month == null || UnifiedLedger.normalizeTime(bill.time).startsWith(month.toString())) &&
                !drinking.containsMatchIn(listOf(bill.title, bill.merchant, bill.description).joinToString(" ")) &&
                (category == CampusBillCategory.ALL || this.category(bill) == category)
        }.distinctBy(BillLedger::key).sortedByDescending { UnifiedLedger.normalizeTime(it.time) }

    fun expense(records: List<Bill>): Long? = runCatching {
        if (records.any { BillLedger.direction(it).isBlank() }) return null
        records.filter { BillLedger.direction(it) == "2" }.fold(0L) { total, bill ->
            val fen = bill.fen ?: return null
            require(fen != Long.MIN_VALUE)
            Math.addExact(total, kotlin.math.abs(fen))
        }
    }.getOrNull()
}
