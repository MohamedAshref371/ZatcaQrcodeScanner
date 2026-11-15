package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.InvoiceHistoryCsv
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsCsv
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser

enum class InvoiceCsvStatus { EXPORTED, CANCELLED, FAILED }

fun interface InvoiceCsvObserver {
    fun complete(status: InvoiceCsvStatus)
}

interface InvoiceCsvActions {
    fun exportHistoryCsv(query: InvoiceHistoryQuery, observer: InvoiceCsvObserver)
    fun exportStatisticsCsv(snapshot: InvoiceStatisticsSnapshot, groupingLabel: String, net: Boolean,
                            observer: InvoiceCsvObserver)
}

/** Shared CSV rules, with iPhone's Arabic labels and parsed QR fields. */
class InvoiceHistoryCsvDocument(hasHash: Boolean) {
    private val csv = InvoiceHistoryCsv(listOf("اسم البائع", "الرقم الضريبي", "تاريخ الفاتورة", "إجمالي الفاتورة",
        "الضريبة", "Hash الفاتورة", "رقم الفاتورة", "التصنيف", "ملاحظة"), hasHash)

    fun hasHash(raw: String): Boolean = ZatcaInvoiceParser.parse(raw).value(6).isNotBlank()
    fun header(): String = csv.header()
    fun record(raw: String, number: String, category: Int, note: String): String {
        val invoice = ZatcaInvoiceParser.parse(raw)
        val fields = (1..6).map(invoice::value) + listOf(number,
            if (category == 0) "" else arabicInvoiceCategory(category), note)
        return csv.record(fields)
    }
}

/** Uses the exact displayed totals and group order, including return deductions. */
class InvoiceStatisticsCsvDocument(private val snapshot: InvoiceStatisticsSnapshot, groupingLabel: String, net: Boolean) {
    private val csv = InvoiceStatisticsCsv(listOf(groupingLabel, "عدد الفواتير",
        if (net) {
            if (snapshot.summary.missingTotal > 0) "الصافي المتاح للإجمالي" else "صافي إجمالي الفواتير"
        } else if (snapshot.summary.missingTotal > 0) "الإجمالي المتاح" else "إجمالي الفواتير",
        if (net) {
            if (snapshot.summary.missingTax > 0) "الصافي المتاح للضريبة" else "صافي الضريبة"
        } else if (snapshot.summary.missingTax > 0) "الضريبة المتاحة" else "الضريبة",
        "فواتير بدون إجمالي صالح", "فواتير بدون ضريبة صالحة"),
        snapshot.summary.missingTotal != 0 || snapshot.groups.any { it.amounts.missingTotal != 0 },
        snapshot.summary.missingTax != 0 || snapshot.groups.any { it.amounts.missingTax != 0 })

    val groupCount: Int get() = snapshot.groups.size
    fun header(): String = csv.header()
    fun group(index: Int): String = snapshot.groups[index].let { row(it.title, it.amounts) }
    fun summary(): String = row("ملخص النتائج", snapshot.summary)

    private fun row(label: String, values: InvoiceStatisticsAmounts): String = csv.record(label, values.count,
        values.total, values.tax, values.missingTotal, values.missingTax, values.ignored)
}
