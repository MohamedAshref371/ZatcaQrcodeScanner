package com.poetscorner.invoiceqr.data

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** Strict, bounded streaming JSON tokens. Never materializes the complete backup in memory. */
class StreamingBackupJsonInput(private val source: Source) : BackupJsonInput {
    private data class Container(val closing: Int, var first: Boolean = true)
    private val containers = mutableListOf<Container>()
    private var peeked: Int? = null
    private var remaining = 700_000

    private fun peek(): Int = peeked ?: (if (source.exhausted()) -1 else source.readByte().toInt() and 255)
        .also { peeked = it }
    private fun take(): Int {
        val value = peek()
        if (value == -1 || --remaining < 0) throw InvalidBackupException()
        peeked = null
        return value
    }
    private fun whitespace() { while (peek() in listOf(32, 9, 10, 13)) take() }
    private fun expect(character: Char) {
        whitespace()
        if (take() != character.code) throw InvalidBackupException()
    }
    override fun beginObject() { expect('{'); containers.add(Container('}'.code)) }
    override fun beginArray() { expect('['); containers.add(Container(']'.code)) }
    override fun endObject() = end('}')
    override fun endArray() = end(']')
    private fun end(character: Char) {
        if (containers.lastOrNull()?.closing != character.code) throw InvalidBackupException()
        expect(character)
        containers.removeAt(containers.lastIndex)
    }
    override fun hasNext(): Boolean {
        val container = containers.lastOrNull() ?: throw InvalidBackupException()
        whitespace()
        if (peek() == container.closing) return false
        if (!container.first) expect(',')
        container.first = false
        whitespace()
        if (peek() == container.closing || peek() == -1) throw InvalidBackupException()
        return true
    }
    override fun nextName(): String = nextString().also { expect(':') }
    override fun nextString(): String {
        expect('"')
        val token = Buffer()
        token.writeByte('"'.code.toByte())
        var escaped = false
        while (true) {
            val byte = take()
            token.writeByte(byte.toByte())
            if (byte == '"'.code && !escaped) break
            escaped = byte == '\\'.code && !escaped
        }
        return try {
            val value = Json.parseToJsonElement(token.readString()) as? JsonPrimitive ?: throw InvalidBackupException()
            if (!value.isString) throw InvalidBackupException()
            value.content
        } catch (error: IllegalArgumentException) { throw InvalidBackupException(error) }
    }
    override fun nextInteger(): Long {
        whitespace()
        val number = buildString {
            if (peek() == '-'.code) append(take().toChar())
            while (peek() in '0'.code..'9'.code) {
                if (length > 20) throw InvalidBackupException()
                append(take().toChar())
            }
        }
        if (!number.matches(Regex("-?(0|[1-9][0-9]*)"))) throw InvalidBackupException()
        return number.toLongOrNull() ?: throw InvalidBackupException()
    }
    override fun resetRecord() { remaining = 700_000 }
    override fun endOfDocument(): Boolean { whitespace(); return containers.isEmpty() && peek() == -1 }
}
