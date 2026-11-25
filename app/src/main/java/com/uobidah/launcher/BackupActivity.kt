package com.uobidah.launcher

import android.os.Bundle
import com.uobidah.launcher.core.BackupCodec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * نسخ احتياطي واستعادة كل الإعدادات (الثيم، الشاشة الرئيسية، المفضلة، الإشعارات والأصوات، الأندية، الجزيرة، الصلاة…).
 * الملفات تُحفظ في مجلد التطبيق الخارجي (لا يحتاج أذونات): Android/data/com.uobidah.launcher/files/backups
 * ويمكن سحبها بـ adb pull. لا تحتوي النسخة مفاتيح API ولا الذاكرة المؤقتة للمباريات.
 */
class BackupActivity : SettingsBase() {

    private fun dir(): File? = try { getExternalFilesDir("backups")?.also { it.mkdirs() } } catch (_: Exception) { null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("💾 النسخ الاحتياطي والاستعادة")
        note("المجلد: " + (dir()?.absolutePath ?: "غير متاح على هذا الجهاز"))
        note("لا تدخل مفاتيح API (المخزّنة مشفرة) في أي نسخة احتياطية.")
        action("💾 إنشاء نسخة احتياطية الآن") { export() }
        section("♻️ استعادة نسخة")
        val files = dir()?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }.orEmpty().take(8)
        if (files.isEmpty()) note("لا توجد نسخ محفوظة بعد.")
        for (f in files) {
            action("♻️ " + f.name + " (" + f.length() / 1024 + " KB)") { confirmRestore(f) }
        }
        section("⟲ إعادة الضبط")
        action("⟲ إعادة ضبط الشاشة الرئيسية والتخصيص إلى الافتراضي (تُنشأ نسخة تلقائية قبلها)") { confirmReset() }
        finishScreen()
    }

    private fun export(): File? {
        val d = dir() ?: run { toast("تعذر الوصول لمجلد التخزين"); return null }
        return try {
            val now = System.currentTimeMillis()
            val installedVersion = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
            val json = BackupCodec.export(prefs.backupSnapshot(), installedVersion, now)
            val name = "uobidah-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now)) + ".json"
            val out = File(d, name)
            out.writeText(json, Charsets.UTF_8)
            // تحقق ذاتي: لا نعلن النجاح قبل أن نقرأ الملف المكتوب ونتحقق منه.
            val check = BackupCodec.parse(out.readText(Charsets.UTF_8))
            if (!check.ok) { out.delete(); toast("فشل التحقق من النسخة: " + check.error); null }
            else { toast("تم الحفظ: $name"); rebuild(""); out }
        } catch (e: Exception) {
            toast("تعذر إنشاء النسخة: " + (e.message ?: ""))
            null
        }
    }

    private fun confirmRestore(f: File) {
        val res = try { BackupCodec.parse(f.readText(Charsets.UTF_8)) } catch (e: Exception) { null }
        if (res == null || !res.ok) { toast("ملف غير صالح: " + (res?.error ?: "تعذرت القراءة")); return }
        val tv = Ui.label(this, "سيتم تطبيق ${res.entries.size} إعداد" + (if (res.skipped.isNotEmpty()) " وتجاهل ${res.skipped.size} غير صالح/محظور" else "") +
            ". يُنشأ نسخة من وضعك الحالي أولاً.", 24f, ut.text)
        Ui.modal(this, ut, "♻️ استعادة " + f.name, tv,
            Btn("✔ استعادة") {
                if (export() != null) {
                    prefs.applyBackup(res.entries)
                    toast("تمت الاستعادة")
                    rebuild("")
                }
            },
            Btn("إلغاء") {})
    }

    private fun confirmReset() {
        val tv = Ui.label(this, "سيُحذف كل التخصيص الحالي بما فيه الأندية المضافة. المفاتيح المشفرة لا تُحذف.", 24f, ut.text)
        Ui.modal(this, ut, "⟲ إعادة الضبط", tv,
            Btn("✔ متابعة") {
                if (export() != null) {
                    prefs.sp.edit().clear().commit()
                    toast("تمت إعادة الضبط")
                    rebuild("")
                }
            },
            Btn("إلغاء") {})
    }
}
