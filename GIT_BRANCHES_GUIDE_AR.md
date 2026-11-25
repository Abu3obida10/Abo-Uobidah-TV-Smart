# فروع Git المحفوظة

الأرشيف `AboUobidahTV_SourceBranches_1.0.48.bundle` يحتوي الفرعين:

- `design24-25-r8-r9`: أحدث خط تصميمي، مع إصلاح Nullable Score.
- `progress9v-backup`: نسخة الرجوع الأصلية دون تعديل.

لاستعادة المستودع:

```bash
git clone AboUobidahTV_SourceBranches_1.0.48.bundle AboUobidahTV
git -C AboUobidahTV checkout design24-25-r8-r9
```

للرجوع إلى progress9v:

```bash
git -C AboUobidahTV checkout progress9v-backup
```
