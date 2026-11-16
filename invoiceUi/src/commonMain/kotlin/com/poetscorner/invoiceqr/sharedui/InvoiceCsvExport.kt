package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.InvoiceHistoryCsv
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsCsv
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser

enum class InvoiceCsvStatus { EXPORTED, CANCELLED, FAILED }

fun interface InvoiceCsvObserver {
    fun complete(status: InvoiceCsvStatus)
}

interface InvoiceCsvActions {
    fun exportHistoryCsv(query: InvoiceHistoryQuery, language: InvoiceLanguage, observer: InvoiceCsvObserver)
    fun exportStatisticsCsv(snapshot: InvoiceStatisticsSnapshot, groupingLabel: String, net: Boolean,
                            language: InvoiceLanguage, observer: InvoiceCsvObserver)
}

/** Shared CSV rules, with localized labels and parsed QR fields. */
class InvoiceHistoryCsvDocument(hasHash: Boolean, private val language: InvoiceLanguage) {
    private val csv = InvoiceHistoryCsv(listOf(language.text("اسم البائع", "Seller name"), language.text("الرقم الضريبي", "VAT number"), language.text("تاريخ الفاتورة", "Invoice date"), language.text("إجمالي الفاتورة", "Invoice total"),
        language.text("الضريبة", "VAT amount"), language.text("Hash الفاتورة", "Invoice hash"), language.text("رقم الفاتورة", "Invoice number"), language.text("التصنيف", "Category"), language.text("ملاحظة", "Note")), hasHash)

    fun hasHash(raw: String): Boolean = ZatcaInvoiceParser.parse(raw).value(6).isNotBlank()
    fun header(): String = csv.header()
    fun record(raw: String, number: String, category: Int, note: String): String {
        val invoice = ZatcaInvoiceParser.parse(raw)
        val fields = (1..6).map(invoice::value) + listOf(number,
            if (category == 0) "" else invoiceCategoryLabel(category, language), note)
        return csv.record(fields)
    }
}

/** Uses the exact displayed totals and group order, including return deductions. */
class InvoiceStatisticsCsvDocument(private val snapshot: InvoiceStatisticsSnapshot, groupingLabel: String, net: Boolean,
                                   private val language: InvoiceLanguage) {
    private val csv = InvoiceStatisticsCsv(listOf(groupingLabel, language.text("عدد الفواتير", "Invoice count"),
        if (net) {
            if (snapshot.summary.missingTotal > 0) language.text("الصافي المتاح للإجمالي", "Available net total") else language.text("صافي إجمالي الفواتير", "Net invoice total")
        } else if (snapshot.summary.missingTotal > 0) language.text("الإجمالي المتاح", "Available total") else language.text("إجمالي الفواتير", "Invoice total"),
        if (net) {
            if (snapshot.summary.missingTax > 0) language.text("الصافي المتاح للضريبة", "Available net VAT") else language.text("صافي الضريبة", "Net VAT")
        } else if (snapshot.summary.missingTax > 0) language.text("الضريبة المتاحة", "Available VAT") else language.text("الضريبة", "VAT amount"),
        language.text("فواتير بدون إجمالي صالح", "Invoices without a valid total"), language.text("فواتير بدون ضريبة صالحة", "Invoices without valid VAT")),
        snapshot.summary.missingTotal != 0 || snapshot.groups.any { it.amounts.missingTotal != 0 },
        snapshot.summary.missingTax != 0 || snapshot.groups.any { it.amounts.missingTax != 0 })

    val groupCount: Int get() = snapshot.groups.size
    fun header(): String = csv.header()
    fun group(index: Int): String = snapshot.groups[index].let { row(it.title, it.amounts) }
    fun summary(): String = row(language.text("ملخص النتائج", "Summary"), snapshot.summary)

    private fun row(label: String, values: InvoiceStatisticsAmounts): String = csv.record(label, values.count,
        values.total, values.tax, values.missingTotal, values.missingTax, values.ignored)
}
