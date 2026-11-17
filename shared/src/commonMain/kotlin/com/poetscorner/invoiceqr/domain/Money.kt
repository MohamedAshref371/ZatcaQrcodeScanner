package com.poetscorner.invoiceqr.domain

import kotlin.jvm.JvmInline

/** Exact fixed-point monetary value with two fractional digits. */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    companion object {
        private val decimal = Regex("-?[0-9]+(?:\\.[0-9]{1,2})?")

        /** Returns null for invalid syntax or values outside the signed Long range in cents. */
        fun parseOrNull(value: String): Money? {
            if (!decimal.matches(value)) return null
            val whole = value.substringBefore('.')
            val fraction = value.substringAfter('.', "").padEnd(2, '0')
            return (whole + fraction).toLongOrNull()?.let(::Money)
        }
    }
}
