package com.poetscorner.invoiceqr.shared

data class BackupInvoice(
    val raw: String,
    val checkedAt: Long,
    val invoiceNumber: String = "",
    val category: Int = 0,
    val note: String = "",
)

data class DecodedBackupInvoice(val record: BackupInvoice, val invoice: ParsedInvoice)

class InvalidHistoryBackup : Exception("Invalid invoice history backup")

fun interface HistoryBackupSource {
    /** A nonempty chunk, or null at end of input. */
    fun readChunk(): String?
}

fun normalizedInvoicePayload(raw: String): String = raw.filterNot { it.isWhitespace() }.trimEnd('=')

/** Wire format used by both apps. Consumers must finish reading before committing a restore. */
object HistoryBackupCodec {
    const val MAX_BYTES = 64L * 1024 * 1024
    const val FORMAT = "invoiceqr-history-backup"
    const val VERSION = 2

    fun header() = """{"format":"$FORMAT","version":$VERSION,"invoices":["""

    fun record(value: BackupInvoice, first: Boolean): String = buildString {
        if (!first) append(',')
        append("""{"raw":""")
        appendQuoted(value.raw)
        append(""","checkedAt":""").append(value.checkedAt)
        append(""","invoiceNumber":""")
        appendQuoted(value.invoiceNumber)
        append(""","category":""").append(value.category)
        append(""","note":""")
        appendQuoted(value.note)
        append('}')
    }

    fun footer() = "]}"

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"', '\\' -> append('\\').append(char)
                in '\u0000'..'\u001F', '\u2028', '\u2029' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> append(char)
            }
        }
        append('"')
    }
}

class HistoryBackupReader(source: HistoryBackupSource) {
    private val json = BackupJsonInput(source)
    private val fields = mutableSetOf<String>()
    private var format: String? = null
    private var version: Long? = null
    private var firstField = true
    private var firstInvoice = true
    private var inInvoices = false
    private var annotated = false
    private var started = false
    private var finished = false

    fun next(): DecodedBackupInvoice? {
        if (finished) return null
        json.resetBudget()
        if (!started) {
            json.take('\uFEFF')
            json.expect('{')
            started = true
        }
        while (true) {
            if (inInvoices) {
                if (json.take(']')) {
                    inInvoices = false
                } else {
                    if (!firstInvoice) json.expect(',')
                    firstInvoice = false
                    return readInvoice()
                }
            } else {
                if (json.take('}')) {
                    val savedVersion = version
                    validBackup(format == HistoryBackupCodec.FORMAT &&
                        savedVersion != null && savedVersion in 1L..HistoryBackupCodec.VERSION.toLong() && "invoices" in fields)
                    validBackup(version != 1L || !annotated)
                    json.end()
                    finished = true
                    return null
                }
                if (!firstField) json.expect(',')
                firstField = false
                val field = json.string(32)
                validBackup(fields.add(field))
                json.expect(':')
                when (field) {
                    "format" -> format = json.string(64)
                    "version" -> version = json.integer()
                    "invoices" -> {
                        json.expect('[')
                        inInvoices = true
                    }
                    else -> throw InvalidHistoryBackup()
                }
            }
        }
    }

    private fun readInvoice(): DecodedBackupInvoice {
        val seen = mutableSetOf<String>()
        var raw: String? = null
        var checkedAt: Long? = null
        var invoiceNumber = ""
        var category = 0
        var note = ""
        json.expect('{')
        if (!json.take('}')) {
            do {
                val field = json.string(32)
                validBackup(seen.add(field))
                json.expect(':')
                when (field) {
                    "raw" -> raw = json.string(100_000)
                    "checkedAt" -> checkedAt = json.integer()
                    "invoiceNumber" -> {
                        annotated = true
                        invoiceNumber = json.string(InvoiceAnnotationRules.MAX_NUMBER)
                    }
                    "category" -> {
                        annotated = true
                        val id = json.integer()
                        validBackup(InvoiceAnnotationRules.categoryIds.any { it.toLong() == id })
                        category = id.toInt()
                    }
                    "note" -> {
                        annotated = true
                        note = json.string(InvoiceAnnotationRules.MAX_NOTE)
                    }
                    else -> throw InvalidHistoryBackup()
                }
            } while (json.take(','))
            json.expect('}')
        }
        val payload = raw ?: throw InvalidHistoryBackup()
        val time = checkedAt ?: throw InvalidHistoryBackup()
        validBackup(time in 0..253402300799999L)
        val invoice = ZatcaInvoiceParser.parse(payload)
        validBackup(invoice.error == null)
        return DecodedBackupInvoice(BackupInvoice(payload, time, invoiceNumber, category, note), invoice)
    }
}

private fun validBackup(valid: Boolean) {
    if (!valid) throw InvalidHistoryBackup()
}

private class BackupJsonInput(private val source: HistoryBackupSource) {
    private var chunk = ""
    private var index = 0
    private var budget = 0
    private var eof = false

    fun resetBudget() { budget = 700_000 }

    private fun peek(): Char? {
        if (index == chunk.length && !eof) {
            val data = source.readChunk()
            if (data == null) eof = true
            else {
                validBackup(data.isNotEmpty())
                chunk = data
                index = 0
            }
        }
        return chunk.getOrNull(index)
    }

    private fun read(): Char {
        val char = peek() ?: throw InvalidHistoryBackup()
        validBackup(budget-- > 0)
        index++
        return char
    }

    private fun whitespace() {
        while (when (peek()) { ' ', '\r', '\n', '\t' -> true; else -> false }) read()
    }

    fun take(char: Char): Boolean {
        whitespace()
        if (peek() != char) return false
        read()
        return true
    }

    fun expect(char: Char) = validBackup(take(char))

    fun end() {
        whitespace()
        validBackup(peek() == null)
    }

    fun string(limit: Int): String {
        expect('"')
        return buildString {
            while (true) {
                val char = read()
                if (char == '"') break
                validBackup(length < limit)
                append(if (char == '\\') escape() else {
                    validBackup(char >= ' ')
                    char
                })
            }
        }
    }

    private fun escape(): Char = when (val char = read()) {
        '"', '\\', '/' -> char
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            var code = 0
            repeat(4) {
                val hex = read()
                val digit = when (hex) {
                    in '0'..'9' -> hex - '0'
                    in 'a'..'f' -> hex - 'a' + 10
                    in 'A'..'F' -> hex - 'A' + 10
                    else -> throw InvalidHistoryBackup()
                }
                code = code * 16 + digit
            }
            code.toChar()
        }
        else -> throw InvalidHistoryBackup()
    }

    fun integer(): Long {
        whitespace()
        val number = buildString {
            if (peek() == '-') append(read())
            val first = read()
            validBackup(first in '0'..'9')
            append(first)
            if (first == '0') validBackup(peek()?.let { it !in '0'..'9' } != false)
            while (peek()?.let { it in '0'..'9' } == true) {
                validBackup(length < 20)
                append(read())
            }
        }
        return number.toLongOrNull() ?: throw InvalidHistoryBackup()
    }
}
