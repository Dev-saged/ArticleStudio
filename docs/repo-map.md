# repo-map — ArticleStudio

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.

## Quick index (ميزة → ملف → ابحث بـ)
- الهوية والألوان → `BrandConfig.kt` → `BrandConfig`
- الثيم، المكونات، الحركة، الأيقونات → `ui/theme/*`، `ui/Components.kt`، `ui/Motion.kt`، `ui/icons/Icons.kt`
- المخططات → `schema/*`؛ المزودون → `engine/ProviderEngine.kt`؛ الصور → `engine/ImageRenderer.kt`؛ التحديث → `engine/Updater.kt`؛ SEO → `XmlBuilder.bodyHtml`
- الرئيسية: لوحة الكرة، الوضع، السجل → `ui/home/*` → `HomeViewModel`، `PitchBoard`
- المقالات، المزودون، التصدير → `ui/screens/{Articles,Providers,Export}.kt`
- التنقل (5 تبويبات)، الحواف `EdgeBar`، الشريط الحي → `ui/nav/AppNav.kt`
- المواقع المتعددة: النماذج الجاهزة، المخزن، القواعد → `engine/Site.kt` → `SiteConfig`، `Niches`، `SiteStore`، `SiteRules`
- النشر المباشر: ناشران (جسر Apps Script، Blogger API بتفويض جوجل) → `engine/Publisher.kt`، `engine/PublishManager.kt`، `engine/GoogleAuth.kt`، `engine/XmlImport.kt`؛ الدليل والإعداد → `ui/screens/Publish.kt` → `PublishGuide`
- لوحة المواقع ومعالج الإضافة (3 خطوات من المخططات) → `ui/screens/Sites.kt` → `SiteForms`، `SitesScreen`
- Room v4 + ترحيلات (site_id، quality، remote_url) → `data/Db.kt`، `data/Entities.kt` → `Migrations`
- المفاتيح/المزودون → `data/{KeyBackup,Providers,SecretStore,Stores}.kt`؛ التوليد → `engine/{Generator,JobRunner}.kt`؛ GitHub → `engine/GitHubClient.kt`
- فهرس المدونة (منع التكرار) → `engine/BlogIndex.kt`؛ مصادر الأخبار → `engine/NewsSources.kt`
- XML بلوجر، التصدير، التسمية، الخدمة الأمامية → `engine/{XmlBuilder,ExportManager,ExportNaming,GenerationService}.kt`

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

- النشر المباشر يمر عبر XML نفسه (`XmlImport.parse`) فمسار التصدير والملف اليدوي واحد؛ روابط «اقرأ أيضاً» لمقالات الدفعة تُحذف لأن بلوجر يولّد الرابط؛ الـ API لا يضبط permalink ولا metaDescription.
- سر الجسر لكل موقع في `SecretStore` باسم `bridge-<siteId>` ويُضمَّن في السكربت فقط.
- فك تشفير المفتاح يحصل مرة واحدة ويُخزَّن في الذاكرة (`SecretStore.cache`)؛ كان يُنفَّذ 4 مرات/ث على الخيط الرئيسي فيعلّق التنقل.
- قوائم Room تقرأ إسقاطاً خفيفاً `ArticleRow` (بلا JSON المقال)؛ الجودة عمود `quality`.

## Open issues
- لم يُجرَّب فعلياً: نشر حقيقي عبر الجسر أو تفويض جوجل (مُختبَر بخادم وهمي)، وسلوك `published` المستقبلي في الجدولة، وبقاء <script> JSON-LD في المحتوى.
- لم يُجرَّب: توليد/تصدير موقع غير الرياضة بمزودات حقيقية؛ سلاسة التنقل على جهاز فعلي.
- لم يُشغَّل على جهاز/محاكي: الخدمة الأمامية، Keystore، الأنيميشن، الأذونات، الإشعار.
- لم تُجرَّب مزودات حقيقية ولا ESPN ولا رفع GitHub ولا استيراد بلوجر (مُختبَر بخادم وهمي فقط).
