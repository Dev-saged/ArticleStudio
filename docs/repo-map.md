# repo-map — ArticleStudio

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.

## Quick index (ميزة → ملف → ابحث بـ)
- الهوية والألوان → `BrandConfig.kt` → `BrandConfig`
- الثيم فاتح/غامق + RTL، الخطوط، الأيقونات → `ui/theme/*`، `ui/icons/Icons.kt`
- مكونات مشتركة وحركة → `ui/Components.kt`، `ui/Motion.kt`
- الرئيسية: لوحة الكرة، الوضع، السجل → `ui/home/*` → `HomeViewModel`، `PitchBoard`
- المقالات، المزودون، التصدير → `ui/screens/{Articles,Providers,Export}.kt`
- التنقل + الشريط الحي → `ui/nav/AppNav.kt`
- محرك المخططات (نماذج/قوائم) → `schema/Schema.kt`، `schema/Engine.kt` → `Schemas`
- Room v2 + ترحيلات → `data/Db.kt`، `data/Entities.kt` → `Migrations`
- استيراد/تصدير المفاتيح بصيغة الموقع → `data/KeyBackup.kt` → `KeyBackup.parse`
- فهرس المدونة (منع التكرار) → `engine/BlogIndex.kt`؛ مصادر الأخبار → `engine/NewsSources.kt`
- تحديث داخلي (فحص/تنزيل/تثبيت) → `engine/Updater.kt`، `ui/UpdateCard.kt`
- حواف النظام (شريطا الأعلى والأسفل) → `ui/nav/AppNav.kt` → `EdgeBar`
- SEO/GEO في المقال → `engine/XmlBuilder.kt` → `bodyHtml`، `jsonLd`، `related`
- تسمية الملفات العربية → `engine/ExportNaming.kt`
- المزودون والمفاتيح والصحة → `data/Providers.kt`، `data/SecretStore.kt`، `data/Stores.kt`
- التبديل التلقائي بين المزودين → `engine/ProviderEngine.kt` → `chat`
- تخطيط وكتابة المقال بمرحلتين → `engine/Generator.kt` → `article`، `plan`
- تنفيذ المهمة والاستئناف → `engine/JobRunner.kt` → `JobRunner`
- الخدمة الأمامية + الإشعار → `engine/GenerationService.kt`
- صور الغلاف/البطاقة → `engine/ImageRenderer.kt`، `engine/SvgDraw.kt`
- XML بلوجر → `engine/XmlBuilder.kt`؛ التصدير → `engine/ExportManager.kt`
- رفع الصور لـ GitHub + فحص التحديث → `engine/GitHubClient.kt`
- الفحص الذاتي على الجهاز → `ui/test/SelfTestScreen.kt` (5 نقرات على عنوان الرئيسية)

## Decisions
- الشعار B؛ التصميم C بألوان A؛ تطبيق أصلي لأن الجافاسكربت يتجمد في الخلفية.
- كل مقال يُحفظ في Room فور اكتماله؛ المهمة تستأنف من `jobs.next` بعد أي توقف.
- التصدير بنوع `text/xml` لأن منتقي بلوجر يعتّم `application/xml`.
- الصور تُرفع بإيداع واحد لفرع `blog-images` وتُثبَّت الروابط بمعرّف الإيداع عبر jsDelivr.
- رقم البناء = `GITHUB_RUN_NUMBER`، وتاج الإصدار `vX.Y.Z-bN` لفحص التحديث.

- تشابه العناوين: ≥3 كلمات مشتركة وتغطية 0.8 أو جاكارد 0.6 (`similarTitles`)؛ العتبة الأدنى قبلت «يفوز على X» مقابل «يفوز على Y».
- التصدير بمراحل (4) وشريط واحد؛ `pushImages(step(text,i,n))`.

- الحواف: الشرائط الصلبة بارتفاع `WindowInsets.systemBars ∪ displayCutout`؛ لا `statusBarsPadding` داخل الشاشات؛ المستودع خاص فالتحديث يحتاج توكناً أو مستودعاً عاماً.
- القالب يولّد BlogPosting وBreadcrumb؛ المقال يضيف FAQPage فقط لتجنب التكرار.

## Solved bugs (never reintroduce)
- تداخل الواجهة مع أشرطة النظام (targetSdk 36 يفرض edge-to-edge): استعمل `EdgeBar` لا حشواً متفرقاً.
- وصف RSS مهرّب (`&lt;p&gt;`): فك الكيانات قبل إزالة الوسوم ثم بعدها. `NewsSources.clean`.
- نص SVG: قراءة سمات `<text>` بعد `nextText()` تعيد null (تُقرأ قبله). `SvgDraw.render`.
- الأرقام معكوسة في الصور: `drawTextRun(rtl=true)` يفرض RTL على الأرقام؛ نستخدم `drawText` مع RLM. `ImageRenderer.textR`.
- تداخل الرقم مع التسمية في بطاقة المعلومات: قياسات `card()` (84/70/56/46).
- انهيار حفظ المفتاح إن تعذّر Keystore: `SecretException` تُلتقط في الواجهة.
- توقيع الإصدار: داخل `create("release")` يشير `storeFile` لخاصية الإعداد (null)؛ نستخدم `keystorePath` من البيئة. ظهر في CI فقط بعد إضافة الأسرار.
- قفزة الكرة لعقدتها القديمة قبل تحديث الحالة: موضع الراحة = العقدة الهدف.

## Open issues
- لم يُشغَّل على جهاز/محاكي: الخدمة الأمامية، Keystore، الأنيميشن، الأذونات، الإشعار.
- لم تُجرَّب مزودات حقيقية ولا ESPN ولا رفع GitHub ولا استيراد بلوجر (مُختبَر بخادم وهمي فقط).
- فشل المزودين جميعاً يجعل المحرك ينتظر حتى 90ث×3 جولات قبل إخفاق الموضوع.
