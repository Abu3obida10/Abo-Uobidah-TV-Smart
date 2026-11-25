# جاهزية النشر — 1.0.43-merge21

- `versionName`: `1.0.43-merge21`
- `versionCode`: `65`
- `applicationId`: `com.uobidah.launcher`
- المصدر المركزي للأرقام: `gradle.properties`.
- الحالة: Source Candidate فقط؛ لا يوجد APK جديد مبني أو موقع داخل الحزمة.
- لا تنشئ GitHub Release قبل نجاح `testDebugUnitTest` و`assembleRelease`، وفحص APK والتوقيع، ثم تجربة التثبيت والتشغيل على Android TV.
- يجب استخدام شهادة المالك نفسها للتحديث فوق النسخة المثبتة؛ لا تحذف التطبيق تلقائيًا عند تعارض الشهادة.
- لا ترفع ملفات keystore أو كلمات المرور إلى GitHub أو ZIP.
