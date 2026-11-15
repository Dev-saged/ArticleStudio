# repo-map — ArticleStudio

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.

## Quick index (ميزة → ملف → ابحث بـ)
- الهوية والألوان → `BrandConfig.kt` → `BrandConfig`
- الثيم فاتح/غامق + RTL، الخطوط، الأيقونات → `ui/theme/*`، `ui/icons/Icons.kt`
- مكونات مشتركة وحركة → `ui/Components.kt`، `ui/Motion.kt`
- الرئيسية: لوحة الكرة، الوضع، السجل → `ui/home/*` → `HomeViewModel`، `PitchBoard`
- المقالات، المزودون، التصدير → `ui/screens/{Articles,Providers,Export}.kt`
- التنقل (5 تبويبات)، الحواف `EdgeBar`، الشريط الحي → `ui/nav/AppNav.kt`
- المواقع المتعددة: النماذج الجاهزة، المخزن، القواعد → `engine/Site.kt` → `SiteConfig`، `Niches`، `SiteStore`، `SiteRules`
- لوحة المواقع ومعالج الإضافة (3 خطوات من المخططات) → `ui/screens/Sites.kt` → `SiteForms`، `SitesScreen`
- محرك المخططات (نماذج/قوائم) → `schema/Schema.kt`، `schema/Engine.kt` → `Schemas`
- Room v3 + ترحيلات (site_id، quality) → `data/Db.kt`، `data/Entities.kt` → `Migrations`
- استيراد/تصدير المفاتيح بصيغة الموقع → `data/KeyBackup.kt` → `KeyBackup.parse`
- فهرس المدونة (منع التكرار) → `engine/BlogIndex.kt`؛ مصادر الأخبار → `engine/NewsSources.kt`
- تحديث داخلي (فحص/تنزيل/تثبيت) → `engine/Updater.kt`، `ui/UpdateCard.kt`
- SEO/GEO في المقال → `engine/XmlBuilder.kt` → `bodyHtml`، `jsonLd`، `related`
- المزودون والمفاتيح والصحة → `data/Providers.kt`، `data/SecretStore.kt`، `data/Stores.kt`
- التبديل التلقائي بين المزودين → `engine/ProviderEngine.kt` → `chat`
- تخطيط وكتابة المقال بمرحلتين → `engine/Generator.kt` → `article`، `plan`
- تنفيذ المهمة والاستئناف → `engine/JobRunner.kt` → `JobRunner`
- صور الغلاف/البطاقة → `engine/ImageRenderer.kt`، `engine/SvgDraw.kt`
- XML بلوجر، التصدير، التسمية، الخدمة الأمامية → `engine/{XmlBuilder,ExportManager,ExportNaming,GenerationService}.kt`
- رفع الصور لـ GitHub + فحص التحديث → `engine/GitHubClient.kt`

## Decisions
- كل موقع: سجل/فهرس/مقالات/مهام/مجلد تصدير مستقلة بمفتاح `siteId`؛ الموقع `default` = سبورت لايف 24 الأصلي ويحتفظ بقائمة `seed`.
- كل مقال يُحفظ في Room فور اكتماله؛ المهمة تستأنف من `jobs.next` بعد أي توقف.
- الصور تُرفع بإيداع واحد لفرع `blog-images` وتُثبَّت الروابط بمعرّف الإيداع عبر jsDelivr.
- رقم البناء = `GITHUB_RUN_NUMBER`، وتاج الإصدار `vX.Y.Z-bN` لفحص التحديث.

- تشابه العناوين: ≥3 كلمات مشتركة وتغطية 0.8 أو جاكارد 0.6 (`similarTitles`)؛ العتبة الأدنى قبلت «يفوز على X» مقابل «يفوز على Y».


## Solved bugs (never reintroduce)
- تعليق التنقل (المزودون/الرئيسية): فك Keystore في حلقة 250ms على الرئيسي + تحميل JSON كل المقالات. لا تعد `observeAll` ولا فك التشفير خارج `SecretStore.cache`.
- `SchemaRules.sanitize` كان يقص المسافات أثناء الكتابة؛ في الحقول استعمل `trim = false`.
- تداخل الواجهة مع أشرطة النظام (targetSdk 36 يفرض edge-to-edge): استعمل `EdgeBar` لا حشواً متفرقاً.
- وصف RSS مهرّب (`&lt;p&gt;`): فك الكيانات قبل إزالة الوسوم ثم بعدها. `NewsSources.clean`.
- الأرقام معكوسة في الصور: استعمل `drawText` مع RLM (`ImageRenderer.textR`).
- توقيع الإصدار: استعمل `keystorePath` من البيئة لا `storeFile` داخل `create("release")`.

- فك تشفير المفتاح يحصل مرة واحدة ويُخزَّن في الذاكرة (`SecretStore.cache`)؛ كان يُنفَّذ 4 مرات/ث على الخيط الرئيسي فيعلّق التنقل.
- قوائم Room تقرأ إسقاطاً خفيفاً `ArticleRow` (بلا JSON المقال)؛ الجودة عمود `quality`.

## Open issues
- لم يُجرَّب: توليد/تصدير موقع غير الرياضة بمزودات حقيقية؛ سلاسة التنقل على جهاز فعلي.
- لم يُشغَّل على جهاز/محاكي: الخدمة الأمامية، Keystore، الأنيميشن، الأذونات، الإشعار.
- لم تُجرَّب مزودات حقيقية ولا ESPN ولا رفع GitHub ولا استيراد بلوجر (مُختبَر بخادم وهمي فقط).
