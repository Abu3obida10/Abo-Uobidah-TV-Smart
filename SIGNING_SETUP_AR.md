# إعداد توقيع Abo Uobidah TV — مفتاح ثابت

إصدار التطبيق لا يولّد مفتاح توقيع جديدًا تلقائيًا. التوقيع الثابت مطلوب للحفاظ على قابلية التحديث فوق النسخة المثبتة.

## الطريقة المفضلة
ضع ملفًا محليًا خارج المشروع في:
`~/.abo-uobidah/signing.properties`

يحتوي على:
```properties
storeFile=/ABS/PATH/to/owner-release.jks
storePassword=
keyAlias=
keyPassword=
```

لا تضع هذا الملف أو ملف JKS داخل ZIP أو Git. البديل للـCI هو ضبط `ABO_UOBIDAH_SIGNING_PROPERTIES` أو متغيرات `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## سلوك الأمان
- بناء `debug` وتشغيل `testDebugUnitTest` لا يتطلبان مفتاح Release؛ غياب المفتاح لا يجب أن يوقفهما.
- بناء `release` يفشل صراحةً عند غياب المفتاح الثابت.
- لا يوجد fallback إلى debug key ولا توليد مفتاح عشوائي.
- مثبت الـAPK لا يحذف التطبيق المثبت تلقائيًا.
- تطبيق التحديث يعتمد على تطابق package/version/SHA-256 وشهادة التوقيع الحالية قبل التثبيت.
