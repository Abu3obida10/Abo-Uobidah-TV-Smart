package com.uobidah.launcher.core

/** ودجت الملاحظات (مرجع R4): قائمة مهام قصيرة بمربعات اختيار، تُحفظ محليًا فقط. منطق بحت قابل للاختبار. */
data class Note(val text: String, val done: Boolean = false)

object Notes {
    const val MAX_NOTES = 12
    const val MAX_TEXT = 80

    fun clean(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT)

    fun encode(list: List<Note>): String =
        MiniJson.stringify(list.take(MAX_NOTES).map { mapOf("t" to it.text, "d" to it.done) })

    /** تالف أو فارغ → قائمة فارغة؛ لا يُحقن شيء افتراضي. */
    fun decode(json: String?): List<Note> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = try { MiniJson.parse(json) as? List<*> } catch (_: Exception) { null } ?: return emptyList()
        val out = ArrayList<Note>()
        for (x in arr) {
            val m = x as? Map<*, *> ?: continue
            val t = clean(m["t"]?.toString().orEmpty())
            if (t.isEmpty()) continue
            out.add(Note(t, m["d"] == true))
            if (out.size >= MAX_NOTES) break
        }
        return out
    }

    /** null إن كان النص فارغًا أو القائمة ممتلئة. */
    fun add(list: List<Note>, text: String): List<Note>? {
        val t = clean(text)
        if (t.isEmpty() || list.size >= MAX_NOTES) return null
        return list + Note(t)
    }

    fun toggle(list: List<Note>, index: Int): List<Note> =
        if (index !in list.indices) list else list.mapIndexed { i, n -> if (i == index) n.copy(done = !n.done) else n }

    fun rename(list: List<Note>, index: Int, text: String): List<Note> {
        val t = clean(text)
        if (index !in list.indices || t.isEmpty()) return list
        return list.mapIndexed { i, n -> if (i == index) n.copy(text = t) else n }
    }

    fun remove(list: List<Note>, index: Int): List<Note> =
        if (index !in list.indices) list else list.filterIndexed { i, _ -> i != index }

    /** المعروض في البطاقة: غير المنجزة أولًا ثم المنجزة، بحد أقصى limit مع بقاء الفهرس الأصلي. */
    fun visible(list: List<Note>, limit: Int): List<Pair<Int, Note>> =
        list.withIndex().sortedBy { it.value.done }.take(limit).map { it.index to it.value }
}
