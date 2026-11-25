# دليل البناء والتثبيت — Abo Uobidah TV 1.0.43-merge21

## المتطلبات

- JDK 17.
- Gradle Wrapper 8.7 المرفق.
- Android SDK Platform 35.
- Android SDK Build Tools 34.0.0.
- Android Gradle Plugin 8.6.1 وKotlin Gradle Plugin 2.1.21 كما في ملفات المشروع.

## مصدر الإصدار

رقم النسخة `appVersionName` ورقم البناء `appVersionCode` معرفان مرة واحدة في `gradle.properties`. لا تعدّل نسخًا مكررة داخل ملفات أخرى. Gradle والمثبت الآمن يقرآن القيم نفسها. معرّف الحزمة `com.uobidah.launcher` ثابت للحفاظ على التحديث فوق التثبيت الحالي.

## بناء Debug

1. ثبّت JDK 17 وAndroid SDK Platform 35 وBuild Tools 34.0.0.
2. شغّل `START_HERE_BUILD_INSTALL.bat` على Windows.
3. يفحص السكربت متطلبات SDK ثم يشغّل اختبارات Android قبل `assembleDebug`.
4. يتولد APK داخل `app/build/outputs/apk/debug/` عند نجاح البناء. Debug مخصص للاختبار ولا يمكن ضمان تحديث نسخة Release به بسبب اختلاف التوقيع.
5. التثبيت اختياري عبر `INSTALL_APK.bat`؛ المثبت يتحقق من الحزمة والإصدار والتوقيع ولا يزيل التطبيق المثبت تلقائيًا.

## بناء Release

يحتاج المفتاح الأصلي نفسه الموضح في `SIGNING_SETUP_AR.md`. لا تستخدم مفتاحًا عشوائيًا أو debug keystore لتحديث نسخة Release المثبتة.

## فحص التلفزيون قبل الاعتماد

استخدم `TV_DEVICE_TEST_CHECKLIST_AR.md` لاختبار التنقل بالريموت والتخطيطات وIPTV والمباريات والصوت وإعادة التشغيل، ثم سجّل النتائج الفعلية. نجاح اختبارات المصدر وحده لا يكفي لاعتماد APK.
