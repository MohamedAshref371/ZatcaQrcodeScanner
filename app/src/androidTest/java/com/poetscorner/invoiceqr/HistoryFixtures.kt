package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ScanEntity
import com.poetscorner.invoiceqr.domain.ZatcaParser
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

internal val historyBaseDay: Long = LocalDate.of(2026, 10, 1).toEpochDay()
internal fun historyFixture(index: Int, seller: String = "متجر ${index % 100}",
    hash: Boolean = index % 2 == 0, date: String = LocalDate.ofEpochDay(historyBaseDay + index % 30).toString() + "T10:00:00",
    time: Long = LocalDate.ofEpochDay(historyBaseDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + index / 10 * 1000L): ScanEntity {
    fun tlv(tag: Int, bytes: ByteArray) = byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
    val values = listOf(1 to seller, 2 to "3" + index.toString().padStart(13, '0') + "3", 3 to date,
        4 to "115.00", 5 to "15.00") + if (hash) listOf(6 to "a".repeat(44), 7 to "b".repeat(88)) else emptyList()
    var bytes = values.fold(byteArrayOf()) { result, (tag, value) -> result + tlv(tag, value.toByteArray(Charsets.UTF_8)) }
    if (hash) bytes += tlv(8, ByteArray(65) { it.toByte() }) + tlv(9, ByteArray(72) { it.toByte() })
    val invoice = ZatcaParser.parse(Base64.getEncoder().encodeToString(bytes))
    check(invoice.error == null)
    return ScanEntity.from(invoice, time)
}
