package com.uobidah.launcher.core

/**
 * JSON صغير بلا اعتماد على Android كي يمكن اختباره على JVM عادي.
 * القيم: Map<String,Any?> / List<Any?> / String / Long / Double / Boolean / null.
 */
object MiniJson {
    class JsonException(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (!p.end()) throw JsonException("trailing data at ${p.pos}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("object expected")

    fun stringify(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long -> sb.append(v.toString())
            is Float -> sb.append(finite(v.toDouble()))
            is Double -> sb.append(finite(v))
            is String -> quote(sb, v)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun finite(d: Double): String =
        if (d.isNaN() || d.isInfinite()) "null" else if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() + ".0" else d.toString()

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun end() = pos >= s.length
        fun ws() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        private fun peek(): Char = if (pos < s.length) s[pos] else throw JsonException("unexpected end")
        private fun expect(c: Char) { if (peek() != c) throw JsonException("expected '$c' at $pos"); pos++ }

        fun value(): Any? {
            ws()
            return when (val c = peek()) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("bad char '$c' at $pos")
            }
        }

        private fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, pos)) throw JsonException("bad literal at $pos")
            pos += w.length; return v
        }

        private fun num(): Any {
            val st = pos
            if (peek() == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(st, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw JsonException("bad number '$t'")
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw JsonException("unterminated string")
                val c = s[pos++]
                if (c == '"') return sb.toString()
                if (c != '\\') { sb.append(c); continue }
                if (pos >= s.length) throw JsonException("bad escape")
                when (val e = s[pos++]) {
                    '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                    'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                    'u' -> {
                        if (pos + 4 > s.length) throw JsonException("bad unicode escape")
                        sb.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: throw JsonException("bad unicode escape"))
                        pos += 4
                    }
                    else -> throw JsonException("bad escape '\\$e'")
                }
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            ws()
            if (peek() == ']') { pos++; return out }
            while (true) {
                out.add(value()); ws()
                if (peek() == ',') { pos++; continue }
                expect(']'); return out
            }
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (peek() == '}') { pos++; return out }
            while (true) {
                ws(); val k = str(); ws(); expect(':')
                out[k] = value(); ws()
                if (peek() == ',') { pos++; continue }
                expect('}'); return out
            }
        }
    }
}
