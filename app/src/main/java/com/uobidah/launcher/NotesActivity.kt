package com.uobidah.launcher

import android.os.Bundle
import android.widget.Toast
import com.uobidah.launcher.core.Note
import com.uobidah.launcher.core.Notes

/** إدارة ودجت الملاحظات (R4): إضافة وتعديل وحذف وتبديل «تم». الحفظ محلي فقط في تفضيلات التطبيق. */
class NotesActivity : SettingsBase() {

    private var list: List<Note> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = Notes.decode(prefs.notesJson)
        setupScreen("📝 الملاحظات")
        section("القائمة (${list.size}/${Notes.MAX_NOTES})")
        if (list.isEmpty()) note("لا توجد ملاحظات بعد. أضف أول ملاحظة من الزر بالأسفل.")
        list.forEachIndexed { i, n ->
            action((if (n.done) "☑  " else "☐  ") + n.text) { itemDialog(i) }
        }
        action("➕ إضافة ملاحظة") { addDialog() }
        finishScreen()
    }

    private fun save(next: List<Note>) {
        prefs.notesJson = Notes.encode(next)
        rebuild("")
    }

    private fun addDialog() {
        if (list.size >= Notes.MAX_NOTES) { Toast.makeText(this, "وصلت للحد الأقصى (${Notes.MAX_NOTES})", Toast.LENGTH_SHORT).show(); return }
        val input = Ui.edit(this, ut, "نص الملاحظة")
        Ui.modal(this, ut, "➕ ملاحظة جديدة", input,
            Btn("✔ حفظ") { Notes.add(list, input.text.toString())?.let { save(it) } },
            Btn("إلغاء") {})
    }

    private fun itemDialog(i: Int) {
        val n = list.getOrNull(i) ?: return
        val input = Ui.edit(this, ut, "نص الملاحظة")
        input.setText(n.text)
        Ui.modal(this, ut, "📝 ملاحظة", input,
            Btn(if (n.done) "↩ غير منجزة" else "✔ تم") { save(Notes.toggle(list, i)) },
            Btn("حفظ النص") { save(Notes.rename(list, i, input.text.toString())) },
            Btn("🗑 حذف") { save(Notes.remove(list, i)) },
            Btn("إلغاء") {})
    }
}
