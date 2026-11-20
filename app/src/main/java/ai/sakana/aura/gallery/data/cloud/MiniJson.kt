package ai.sakana.aura.gallery.data.cloud

/**
 * Tiny JSON reader/writer. org.json is stubbed in plain JVM unit tests, so the manifest code uses this
 * instead. Parsed values: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null.
 */
object MiniJson {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        require(parser.atEnd()) { "Unexpected trailing JSON content at ${parser.position}" }
        return value
    }

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean -> out.append(value.toString())
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Float -> out.append(if (value.isFinite()) value.toString() else "null")
            is Double -> out.append(if (value.isFinite()) value.toString() else "null")
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    quote(out, k.toString()); out.append(':'); append(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) { if (!first) out.append(','); first = false; append(out, v) }
                out.append(']')
            }
            else -> quote(out, value.toString())
        }
    }

    private fun quote(out: StringBuilder, s: String) {
        out.append('"')
        for (ch in s) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch.code < 0x20 -> out.append(String.format("\\u%04x", ch.code))
                else -> out.append(ch)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        var position = 0
        fun atEnd() = position >= s.length

        fun skipWhitespace() { while (position < s.length && s[position].isWhitespace()) position++ }

        fun readValue(): Any? {
            skipWhitespace()
            require(!atEnd()) { "Unexpected end of JSON" }
            return when (val c = s[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw IllegalArgumentException("Unexpected '$c' at $position")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, position)) { "Bad literal at $position" }
            position += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            position++ // {
            skipWhitespace()
            if (peek() == '}') { position++; return map }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "Expected key at $position" }
                val key = readString()
                skipWhitespace()
                require(peek() == ':') { "Expected ':' at $position" }
                position++
                map[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> { position++; return map }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $position")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            position++ // [
            skipWhitespace()
            if (peek() == ']') { position++; return list }
            while (true) {
                list += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> { position++; return list }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $position")
                }
            }
        }

        private fun readString(): String {
            position++ // opening quote
            val out = StringBuilder()
            while (true) {
                require(!atEnd()) { "Unterminated string" }
                val c = s[position++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(!atEnd()) { "Unterminated escape" }
                        when (val e = s[position++]) {
                            '"' -> out.append('"'); '\\' -> out.append('\\'); '/' -> out.append('/')
                            'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                            'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                            'u' -> {
                                require(position + 4 <= s.length) { "Bad unicode escape" }
                                out.append(s.substring(position, position + 4).toInt(16).toChar())
                                position += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = position
            if (s[position] == '-') position++
            while (position < s.length && (s[position].isDigit() || s[position] in ".eE+-")) position++
            val text = s.substring(start, position)
            return if (text.any { it in ".eE" }) text.toDouble() else text.toLong()
        }

        private fun peek(): Char = if (atEnd()) '\u0000' else s[position]
    }
}
