package com.poetscorner.invoiceqr.domain

import kotlin.jvm.JvmInline

/** An exact signed number of cents, including values and sums outside Long range. */
@JvmInline
value class ExactAmount private constructor(private val cents: String) : Comparable<ExactAmount> {
    private val negative get() = cents.startsWith('-')
    private val magnitude get() = if (negative) cents.substring(1) else cents

    operator fun unaryMinus(): ExactAmount = when {
        this == ZERO -> ZERO
        negative -> ExactAmount(magnitude)
        else -> ExactAmount("-$cents")
    }

    override fun compareTo(other: ExactAmount): Int {
        if (negative != other.negative) return if (negative) -1 else 1
        val comparison = compareMagnitude(magnitude, other.magnitude)
        return if (negative) -comparison else comparison
    }

    operator fun plus(other: ExactAmount): ExactAmount {
        val left = cents.toLongOrNull()
        val right = other.cents.toLongOrNull()
        if (left != null && right != null) {
            val sum = left + right
            if (((left xor sum) and (right xor sum)) >= 0) return ExactAmount(sum.toString())
        }
        val a = magnitude
        val b = other.magnitude
        if (negative == other.negative) return fromMagnitude(combine(a, b, subtract = false), negative)
        val comparison = compareMagnitude(a, b)
        return when {
            comparison > 0 -> fromMagnitude(combine(a, b, subtract = true), negative)
            comparison < 0 -> fromMagnitude(combine(b, a, subtract = true), other.negative)
            else -> ZERO
        }
    }

    fun toPlainString(): String {
        val digits = magnitude.padStart(3, '0')
        return (if (negative) "-" else "") + digits.dropLast(2) + "." + digits.takeLast(2)
    }

    override fun toString(): String = toPlainString()

    companion object {
        val ZERO = ExactAmount("0")
        private val invoiceDecimal = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

        fun parseInvoiceAmount(value: String): ExactAmount? {
            if (!invoiceDecimal.matches(value)) return null
            Money.parseOrNull(value)?.let { return ExactAmount(it.cents.toString()) }
            val digits = value.substringBefore('.') + value.substringAfter('.', "").padEnd(2, '0')
            return fromMagnitude(digits, negative = false)
        }

        private fun fromMagnitude(digits: String, negative: Boolean): ExactAmount {
            val magnitude = digits.trimStart('0')
            if (magnitude.isEmpty()) return ZERO
            return ExactAmount(if (negative) "-$magnitude" else magnitude)
        }

        private fun compareMagnitude(a: String, b: String): Int =
            if (a.length == b.length) a.compareTo(b) else a.length.compareTo(b.length)

        // Subtraction is only called with a >= b. Build digits from right to left.
        private fun combine(a: String, b: String, subtract: Boolean): String {
            val digits = StringBuilder(maxOf(a.length, b.length) + 1)
            var carry = 0
            for (position in 0 until maxOf(a.length, b.length)) {
                val left = a.getOrNull(a.lastIndex - position)?.digitToInt() ?: 0
                val right = b.getOrNull(b.lastIndex - position)?.digitToInt() ?: 0
                val digit = left + (if (subtract) -right else right) + carry
                carry = when {
                    digit < 0 -> -1
                    digit >= 10 -> 1
                    else -> 0
                }
                digits.append((digit - carry * 10).digitToChar())
            }
            if (carry > 0) digits.append(carry.digitToChar())
            return digits.reverse().toString()
        }
    }
}
