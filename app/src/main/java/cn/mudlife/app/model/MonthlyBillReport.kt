package cn.mudlife.app.model

import cn.mudlife.campuscard.Bill
import cn.mudlife.campuscard.BillLedger
import cn.mudlife.campuscard.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class MonthlyBillReport(
    val month: YearMonth,
    val until: Int,
    val totalFen: Long?,
    val expenseCount: Int,
    val categoryTotals: Map<CampusBillCategory, Long>,
    private val dailyByCategory: Map<CampusBillCategory, Map<Int, Long>>,
    val notes: List<String>
) {
    fun dailyTotals(category: CampusBillCategory = CampusBillCategory.ALL): Map<Int, Long> =
        dailyByCategory[category].orEmpty()

    companion object {
        fun build(
            month: YearMonth, records: List<Bill>, complete: Boolean, previousComplete: Boolean,
            today: LocalDate = LocalDate.now()
        ): MonthlyBillReport {
            val currentMonth = YearMonth.from(today)
            val until = when {
                month == currentMonth -> today.dayOfMonth
                month < currentMonth -> month.lengthOfMonth()
                else -> 0
            }
            val current = ledgerMonth(records, month, until, complete && until > 0)
            val total = current.total
            if (total == null) return MonthlyBillReport(month, until, null, current.count, emptyMap(), emptyMap(),
                listOf("本月流水尚未核对完整，暂时无法汇总。"))
            val byCategory = current.expenses.groupBy { it.category }
            val categoryTotals = byCategory.entries.sortedByDescending { it.value.totalFen() }
                .associate { it.key to it.value.totalFen() }
            val daily = byCategory.mapValues { it.value.daily() } + (CampusBillCategory.ALL to current.expenses.daily())
            val previousMonth = month.minusMonths(1)
            val last = if (month == currentMonth) minOf(until - 1, previousMonth.lengthOfMonth()) else until
            val previous = if (previousComplete && current.count >= 3 && last > 0)
                ledgerMonth(records, previousMonth, previousMonth.lengthOfMonth(), true) else null
            val comparison = previous?.takeIf { it.total != null && it.present }?.let {
                val partial = month == currentMonth
                Comparison(previousMonth, if (partial) last else null,
                    if (partial) current.expenses.filter { r -> r.day <= last } else current.expenses,
                    if (partial) it.expenses.filter { r -> r.day <= last } else it.expenses)
            }
            return MonthlyBillReport(month, until, total, current.count, categoryTotals, daily,
                makeNotes(month, until, month == currentMonth, current, comparison))
        }
    }
}

private data class Expense(val name: String, val category: CampusBillCategory, val day: Int, val minute: Int?, val fen: Long)
private data class MonthEntries(val expenses: List<Expense>, val count: Int, val total: Long?,
                                val topups: List<Long>?, val present: Boolean)

private fun ledgerMonth(records: List<Bill>, month: YearMonth, until: Int, complete: Boolean): MonthEntries {
    val rows = CampusBillLedger.select(records, month).filterNot(DeviceExpenses::pending)
    val spending = rows.filter { BillLedger.direction(it) == "2" }
    val expenses = spending.mapNotNull { bill ->
        val fen = bill.fen?.takeUnless { it == Long.MIN_VALUE } ?: return@mapNotNull null
        val time = UnifiedLedger.normalizeTime(bill.time)
        val day = coveredDay(time, until) ?: return@mapNotNull null
        val hh = time.substringAfter(' ', "").take(5)
        val minute = if (clockTime.matches(hh)) hh.take(2).toInt() * 60 + hh.takeLast(2).toInt() else null
        Expense(bill.title.trim(), CampusBillLedger.category(bill), day, minute, abs(fen))
    }
    val total = if (complete && expenses.size == spending.size) CampusBillLedger.expense(rows) else null
    val topups = rows.filter { BillLedger.direction(it) == "1" && CampusBillLedger.category(it) == CampusBillCategory.RECHARGE }
    val amounts = topups.mapNotNull { bill ->
        if (coveredDay(UnifiedLedger.normalizeTime(bill.time), until) == null) return@mapNotNull null
        bill.fen?.takeUnless { it == Long.MIN_VALUE }?.let(::abs)
    }
    val verifiedTopups = amounts.takeIf { it.size == topups.size && runCatching { it.fold(0L, Math::addExact) }.isSuccess }
    return MonthEntries(expenses, spending.size, total, verifiedTopups, rows.isNotEmpty())
}

private fun coveredDay(normalizedTime: String, until: Int): Int? =
    runCatching { LocalDate.parse(normalizedTime.take(10)).dayOfMonth }.getOrNull()?.takeIf { it in 1..until }

private val clockTime = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
private val schoolFees = setOf("宿舍电费", "宿舍水费", "宿舍网费", "校园网费", "校园网月费", "校园网套餐费")
private val hall = Regex("^(第?[一二三四五六七八九十百零〇0-9]+食堂)")
private val genericTitle = Regex("消费|当日合计|充值|待核实|支付|未知|^饮水$|^洗澡$|^吹风$")

// Called after the complete sum has passed the ledger's overflow and sign checks.
private fun List<Expense>.totalFen() = sumOf { it.fen }
private fun List<Expense>.daily() = groupBy { it.day }.mapValues { it.value.totalFen() }
private fun List<Expense>.dates() = map { it.day }.distinct().size
private fun List<Expense>.of(category: CampusBillCategory) = filter { it.category == category }
private fun price(fen: Long) = Money.yuan(fen)
private fun percent(fen: Long, total: Long) =
    String.format(Locale.ROOT, "%.1f", 100.0 * fen / total).removeSuffix(".0") + "%"
private fun direction(delta: Long) = if (delta > 0) "多花" else "少花"

private data class Comparison(val previousMonth: YearMonth, val lastDay: Int?,
                              val now: List<Expense>, val before: List<Expense>) {
    val currentTotal = now.totalFen()
    val previousTotal = before.totalFen()
    val delta = currentTotal - previousTotal
    val period = when (lastDay) { null -> "整月"; 1 -> "1 日"; else -> "1–${lastDay} 日" }
    val prefix = if (lastDay == null) "比 ${previousMonth.monthValue} 月" else "${period}比上月同期"
}

private fun makeNotes(month: YearMonth, until: Int, partial: Boolean, entries: MonthEntries,
                      comparison: Comparison?): List<String> {
    val rows = entries.expenses
    val total = rows.totalFen()
    if (rows.isEmpty()) return listOf("这段时间还没有已确认的支出记录。")
    if (rows.size <= 2) return listOf("目前有 ${rows.size} 笔已确认支出，合计 ${price(total)} 元。")
    return buildList {
        primarySpend(rows, total)?.let(::add)
        comparison?.let { add(comparisonNote(it)) }
        serviceNotes(rows, comparison)
        mealNotes(rows)
        calendarNotes(month, until, partial, rows)
        entries.topups?.takeIf { it.isNotEmpty() }?.let { add("这个月有 ${it.size} 笔充值，共 ${price(it.sum())} 元。") }
        if (isEmpty()) add("这段账单里有 ${rows.size} 笔支出，合计 ${price(total)} 元。")
    }
}

private fun primarySpend(rows: List<Expense>, total: Long): String? {
    val largest = rows.maxByOrNull { it.fen } ?: return null
    if (largest.fen >= 3000 && largest.fen >= total * .35)
        return "${largest.day} 日的${largest.name.ifBlank { largest.category.label }}花了 ${price(largest.fen)} 元，占已记支出的 ${percent(largest.fen,total)}。"
    val groups = rows.groupBy { it.category }.entries.sortedByDescending { it.value.totalFen() }
    val first = groups.first()
    val a = first.value.totalFen()
    val second = groups.getOrNull(1)
    val b = second?.value?.totalFen() ?: 0L
    if (a > b && a >= total * .5 && first.value.size >= 3)
        return "${first.key.label}占已记支出的 ${percent(a,total)}，${first.value.size} 笔共 ${price(a)} 元。"
    if (second != null && total > 0 && b >= total * .25 && a + b >= total * .75 && a - b <= total * .15 &&
        b != groups.getOrNull(2)?.value?.totalFen())
        return "${first.key.label}和${second.key.label}合起来是 ${price(a+b)} 元，占已记支出的 ${percent(a+b,total)}。"
    return null
}

private fun comparisonNote(c: Comparison): String {
    if (c.previousTotal > 0 && c.currentTotal > 0 && abs(c.delta) >= 500) {
        val all = c.now + c.before
        val reversal = schoolFees.mapNotNull { name ->
            val bills = all.filter { it.name == name }
            if (bills.none { it.fen >= 1500 } || bills.distinctBy { it.category }.size != 1) return@mapNotNull null
            val currentFee = c.now.filter { it.name == name }.totalFen()
            val priorFee = c.before.filter { it.name == name }.totalFen()
            val rest = (c.currentTotal - currentFee) - (c.previousTotal - priorFee)
            if ((rest > 0) == (c.delta > 0) || abs(rest) < 500) null
            else Triple(name, rest, abs(currentFee - priorFee))
        }.maxByOrNull { it.third }
        if (reversal != null) return "${c.prefix}${direction(c.delta)} ${price(abs(c.delta))} 元。两个月都扣除${reversal.first}后，其余支出反而${if (reversal.second > 0) "多" else "少"}了 ${price(abs(reversal.second))} 元。"
    }
    val changes = CampusBillCategory.entries.filter { it != CampusBillCategory.ALL }.map { cat ->
        cat to (c.now.of(cat).totalFen() - c.before.of(cat).totalFen())
    }.filter { c.delta != 0L && (it.second > 0) == (c.delta > 0) }.sortedByDescending { abs(it.second) }
    val change = changes.firstOrNull()
    if (change != null && abs(change.second) >= 500 && change.second != changes.getOrNull(1)?.second) {
        val period = if (c.lastDay == null) "跟上月相比" else "两个月都看 ${c.period}"
        return "${period}，${change.first.label}${direction(change.second)} ${price(abs(change.second))} 元，是${if (change.second > 0) "增加" else "减少"}最多的一类。"
    }
    return if (c.delta == 0L) "${c.prefix}持平。" else "${c.prefix}${direction(c.delta)} ${price(abs(c.delta))} 元。"
}

private fun MutableList<String>.serviceNotes(rows: List<Expense>, c: Comparison?) {
    val water = rows.of(CampusBillCategory.WATER)
    val waterAmount = water.totalFen()
    val waterComparison = c?.let {
        val a = it.now.of(CampusBillCategory.WATER)
        val b = it.before.of(CampusBillCategory.WATER)
        val fa = a.totalFen()
        val fb = b.totalFen()
        val countChange = a.size - b.size
        val costChange = fa - fb
        if (minOf(a.size,b.size) >= 10 && minOf(a.dates(),b.dates()) >= 2 && minOf(fa,fb) >= 20 &&
            abs(countChange) >= 3 && abs(costChange) >= 30 && (countChange > 0) != (costChange > 0)) {
            val period = if (it.lastDay == null) "和 ${it.previousMonth.monthValue} 月相比" else "两个 ${it.period} 相比"
            "${period}，饮水从 ${b.size} 笔${if (countChange > 0) "增至" else "减至"} ${a.size} 笔，金额却从 ${price(fb)} 元${if (costChange > 0) "升至" else "降至"} ${price(fa)} 元。"
        } else null
    }
    when {
        waterComparison != null -> add(waterComparison)
        water.size >= 10 && waterAmount in 1..500 && waterAmount <= rows.totalFen() * .10 ->
            add("饮水记了 ${water.size} 笔，加起来 ${price(waterAmount)} 元。")
        water.size >= 10 && water.dates() >= 7 ->
            add("有 ${water.dates()} 天留下了饮水记录，合计 ${water.size} 笔。")
    }
    val waterOnly = rows.groupBy { it.day }.values.filter { group ->
        group.all { it.category == CampusBillCategory.WATER } && group.totalFen() > 0
    }
    if (waterOnly.size >= 3 && waterOnly.sumOf { it.size } >= 8)
        add("账单里有 ${waterOnly.size} 天只记了饮水，合计 ${price(waterOnly.sumOf { it.totalFen() })} 元。")

    val bath = rows.of(CampusBillCategory.BATH)
    val dryer = rows.of(CampusBillCategory.DRYER)
    if (bath.size >= 3 && bath.totalFen() > 0) {
        val avg = (bath.totalFen().toDouble() / bath.size).roundToLong()
        add("洗浴共 ${price(bath.totalFen())} 元，${bath.size} 笔平均每笔 ${price(avg)} 元。")
    }
    if (c != null) {
        val a = c.now.of(CampusBillCategory.BATH)
        val b = c.before.of(CampusBillCategory.BATH)
        if (minOf(a.size,b.size) >= 5 && abs(a.size-b.size) <= 1 && abs(a.totalFen()-b.totalFen()) >= 500 &&
            abs(a.totalFen().toDouble()/a.size - b.totalFen().toDouble()/b.size) >= 30) {
            val period = if (c.lastDay == null) "" else "两个月都看 ${c.period}，"
            add("${period}上月洗浴 ${b.size} 笔共 ${price(b.totalFen())} 元，这个月 ${a.size} 笔共 ${price(a.totalFen())} 元。")
        }
    }
    val evening = bath.filter { (it.minute ?: -1) >= 20 * 60 }
    if (bath.size >= 6 && bath.all { it.minute != null } && evening.dates() >= 4 && evening.size >= .75 * bath.size)
        add("${bath.size} 笔洗浴记录里，有 ${evening.size} 笔是在晚上 8 点到午夜之间扣的。")
    if (dryer.size >= 3 && dryer.totalFen() > 0) {
        add("这段时间有 ${dryer.size} 笔吹风记录，共 ${price(dryer.totalFen())} 元。")
        val showersByDay = bath.groupBy { it.day }
        val matched = dryer.filter { d ->
            d.minute != null && showersByDay[d.day].orEmpty().any { b ->
                b.minute != null && (d.minute - b.minute) in 1..15
            }
        }
        if (matched.dates() >= 3) add("有 ${matched.dates()} 天，洗浴扣款后 15 分钟内，账单里又多了一笔吹风机消费。")
    }
}

private fun MutableList<String>.mealNotes(rows: List<Expense>) {
    val food = rows.of(CampusBillCategory.FOOD)
    if (food.size >= 4) {
        val halls = food.mapNotNull { row -> hall.find(row.name)?.value?.let { it to row } }
            .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }
        val h = halls.firstOrNull()
        if (h != null && h.value.size >= 4 && h.value.size >= food.size * .6 && h.value.size != halls.getOrNull(1)?.value?.size &&
            h.value.dates() >= 3 && h.value.none { it.name == h.key }) {
            val shops = h.value.distinctBy { it.name }.size
            if (shops >= 2) {
                val count = if (h.value.size == food.size) "${food.size} 笔饮食消费都来自" else "${food.size} 笔饮食消费中，有 ${h.value.size} 笔来自"
                add("${count}${h.key}的 ${shops} 家商户。")
            }
        }
        val prices = food.groupBy { it.fen }.entries.sortedByDescending { it.value.size }
        val most = prices.first()
        if (most.key > 0 && most.value.size != prices.getOrNull(1)?.value?.size &&
            most.value.size >= food.size * .5 && most.value.dates() >= 3)
            add("${food.size} 笔饮食消费里，有 ${most.value.size} 笔的金额都是 ${price(most.key)} 元。")
    }
    if (food.size >= 5 && food.totalFen() > 0)
        add("${food.size} 笔饮食消费，平均每笔 ${price((food.totalFen().toDouble()/food.size).roundToLong())} 元。")
    else {
        val early = food.filter { it.minute != null && it.minute < 9 * 60 }
        if (early.dates() >= 3) add("有 ${early.dates()} 天在上午 9 点前记下了饮食消费，这些流水共 ${price(early.totalFen())} 元。")
    }
    val shops = rows.filter { (it.category == CampusBillCategory.FOOD || it.category == CampusBillCategory.OTHER) &&
        it.name.isNotBlank() && it.name != it.category.label && !genericTitle.containsMatchIn(it.name) }
        .groupBy { it.name }.entries.sortedByDescending { it.value.size }
    val first = shops.firstOrNull()
    if (first != null && first.value.size >= 4 && first.value.size != shops.getOrNull(1)?.value?.size && first.value.dates() >= 3)
        add("「${first.key}」在 ${first.value.dates()} 个日期出现过，共 ${first.value.size} 笔。")
}

private fun MutableList<String>.calendarNotes(month: YearMonth, until: Int, partial: Boolean, rows: List<Expense>) {
    val days = rows.daily()
    val rank = days.entries.sortedByDescending { it.value }
    if (rank.size >= 3) {
        val peak = rank.first()
        val middle = (rank[(rank.size-1)/2].value.toDouble()+rank[rank.size/2].value)/2
        if (peak.value != rank[1].value && peak.value >= 500 && peak.value >= rows.totalFen() * .15 && peak.value >= 2 * middle)
            add("${month.monthValue} 月 ${peak.key} 日是支出最高的一天，${rows.count { it.day == peak.key }} 笔合计 ${price(peak.value)} 元。")
    }
    var run = 0
    var longest = 0
    var end = 0
    var weekends = 0
    var weekendFen = 0L
    for (d in 1..until) {
        run = if (d in days) run + 1 else 0
        if (run > longest) { longest = run; end = d }
        if (month.atDay(d).dayOfWeek.value >= 6) {
            weekends++
            weekendFen += days[d] ?: 0L
        }
    }
    when {
        longest >= 4 -> add("从 ${end-longest+1} 日到 ${end} 日，连续 ${longest} 天都有支出记录。")
        until >= 7 && days.size >= 3 -> add("这 ${until} 天里，有 ${days.size} 天能在账单中找到支出。")
    }
    if (!partial && weekends >= 4 && until-weekends >= 4) {
        val weekend = weekendFen.toDouble()/weekends
        val weekday = (rows.totalFen()-weekendFen).toDouble()/(until-weekends)
        if (abs(weekend-weekday) >= 100 && maxOf(weekend,weekday) >= 1.5 * minOf(weekend,weekday))
            add("按日历天平摊，周末日均 ${price(weekend.roundToLong())} 元，周一至周五日均 ${price(weekday.roundToLong())} 元。")
    }
}
