# repo-map — ArticleStudio

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.

## Quick index (ميزة → ملف → ابحث بـ)
- الهوية والألوان → `BrandConfig.kt` → `BrandConfig`
- OKLCH → sRGB → `ui/theme/Oklch.kt` → `toColor`
- الثيم فاتح/غامق + RTL → `ui/theme/Theme.kt` → `ArticleStudioTheme`
- الخطوط والأرقام → `ui/theme/Type.kt` → `ltr`
- الأيقونات (مجموعة واحدة) → `ui/icons/Icons.kt` → `Icons`
- مكونات مشتركة → `ui/Components.kt` → `PrimaryButton`
- حركة الدخول والخلفية → `ui/Motion.kt` → `stagger`
- لوحة الكرة والمزودين → `ui/home/PitchBoard.kt` → `PitchBoard`
- الرئيسية + محاكاة المعاينة → `ui/home/HomeViewModel.kt`، `HomeScreen.kt`
- التنقل + الشريط الحي → `ui/nav/AppNav.kt` → `AppNav`
- محرك المخططات → `schema/Schema.kt`، `schema/Engine.kt` → `SchemaForm`
- Room v1 → `data/Db.kt`، `data/Entities.kt` → `Migrations`
- مفاتيح مشفّرة → `data/SecretStore.kt`؛ المزودون → `data/Providers.kt`
- الفحص الذاتي → `ui/test/SelfTestScreen.kt` (5 نقرات على عنوان الرئيسية)

## Decisions
- الشعار B (عمود الخبر + كرة)، الأيقونة التكيفية: ذهبي/أبيض/أحمر على كحلي.
- التصميم: مفهوم C (لوحة تكتيكية) بألوان A (كحلي/أحمر/ذهبي)، الأحمر ثابت خلف الشريط الحي لضمان التباين.
- التحويل إلى تطبيق أصلي بدل PWA: لأن الجافاسكربت يتجمد في الخلفية؛ الخدمة الأمامية هي الحل.
- `HomeViewModel` يحاكي التوليد ببيانات تجريبية إلى أن يُربط المحرك.

## Solved bugs (never reintroduce)
- (لا شيء بعد)

## Open issues
- المحرك الحقيقي (مزودون، فشل وتبديل)، الخدمة الأمامية، تصدير XML بـ text/xml، رفع الصور: لم تُنفَّذ بعد.
- لم يُشغَّل التطبيق على جهاز أو محاكي؛ تحقق منه: بناء debug وrelease وخمسة اختبارات وحدة فقط.
- اختبار ترحيل Room يحتاج جهازاً (androidTest) ولا ترحيلات بعد.
