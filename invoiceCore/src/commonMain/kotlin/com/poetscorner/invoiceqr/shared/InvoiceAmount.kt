package com.poetscorner.invoiceqr.shared

private val invoiceAmountPattern = Regex("[0-9]+(\\.[0-9]{1,2})?")

internal fun invoiceAmountCents(value: String): String? {
    if (!invoiceAmountPattern.matches(value)) return null
    return (value.substringBefore('.') + value.substringAfter('.', "").padEnd(2, '0'))
        .trimStart('0').ifEmpty { "0" }
}

/** Signed arbitrary-precision cents. Invoice fields can exceed a fixed-width decimal. */
class InvoiceAmount private constructor(private val cents: String, private val negative: Boolean) : Comparable<InvoiceAmount> {
    override fun compareTo(other: InvoiceAmount): Int {
        if (negative != other.negative) return if (negative) -1 else 1
        val magnitude = compareMagnitudes(cents, other.cents)
        return if (negative) -magnitude else magnitude
    }

    operator fun unaryMinus(): InvoiceAmount = if (cents == "0") this else InvoiceAmount(cents, !negative)

    operator fun plus(other: InvoiceAmount): InvoiceAmount {
        if (cents == "0") return other
        if (other.cents == "0") return this
        if (negative == other.negative) return InvoiceAmount(addDigits(cents, other.cents, subtract = false), negative)
        val comparison = compareMagnitudes(cents, other.cents)
        if (comparison == 0) return ZERO
        val larger = if (comparison > 0) this else other
        val smaller = if (comparison > 0) other else this
        return InvoiceAmount(addDigits(larger.cents, smaller.cents, subtract = true), larger.negative)
    }

    fun toPlainString(): String {
        val digits = cents.padStart(3, '0')
        val sign = if (negative) "-" else ""
        return sign + digits.dropLast(2) + "." + digits.takeLast(2)
    }

    companion object {
        val ZERO = InvoiceAmount("0", false)
        fun parse(value: String): InvoiceAmount? = invoiceAmountCents(value)?.let { InvoiceAmount(it, false) }
    }
}

private fun compareMagnitudes(first: String, second: String): Int =
    if (first.length != second.length) first.length.compareTo(second.length) else first.compareTo(second)

/** Work from right to left. Subtraction callers pass the larger magnitude first. */
private fun addDigits(first: String, second: String, subtract: Boolean): String {
    val length = maxOf(first.length, second.length)
    val digits = StringBuilder(length + 1)
    var carry = 0
    for (offset in 1..length) {
        val a = first.getOrNull(first.length - offset)?.digitToInt() ?: 0
        val b = second.getOrNull(second.length - offset)?.digitToInt() ?: 0
        if (subtract) {
            val difference = a - b - carry
            carry = if (difference < 0) 1 else 0
            digits.append((difference + 10 * carry).digitToChar())
        } else {
            val sum = a + b + carry
            carry = sum / 10
            digits.append((sum % 10).digitToChar())
        }
    }
    if (!subtract && carry != 0) digits.append(carry.digitToChar())
    return digits.reverse().toString().trimStart('0').ifEmpty { "0" }
}
