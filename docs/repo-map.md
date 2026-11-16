# repo-map — ArticleStudio

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.

## Quick index (ميزة → ملف → ابحث بـ)
- الهوية والألوان → `BrandConfig.kt` → `BrandConfig`
- الثيم، المكونات، الحركة، الأيقونات → `ui/theme/*`، `ui/Components.kt`، `ui/Motion.kt`، `ui/icons/Icons.kt`
- المخططات → `schema/*`؛ الصور → `engine/ImageRenderer.kt`؛ التحديث → `engine/Updater.kt`؛ SEO → `XmlBuilder.bodyHtml`؛ الشاشات → `ui/screens/*`
- الرئيسية → `ui/home/*` → `HomeViewModel`، `PitchBoard`
- التنقل، `EdgeBar` → `ui/nav/AppNav.kt`
- المواقع المتعددة: النماذج الجاهزة، المخزن، القواعد → `engine/Site.kt` → `SiteConfig`، `Niches`، `SiteStore`، `SiteRules`
- النشر المباشر: ناشر واحد (Blogger API بتفويض جوجل) → `engine/Publisher.kt`، `engine/PublishManager.kt`، `engine/GoogleAuth.kt`، `engine/XmlImport.kt`؛ الدليل والإعداد → `ui/screens/Publish.kt` → `PublishGuide`
- المسار التلقائي بعد التوليد → `engine/AutoPipeline.kt` (`PostRun`، يستدعيه `JobRunner.execute`)؛ المؤقت الدوري → `engine/AutoRun.kt` (`AutoPlan`، `AutoRunner.tick`، `AutoWorker`)؛ حقول `auto*` في `SiteConfig`
- محرر الموقع (ورقة واحدة + زر «تعبئة تلقائية») → `ui/screens/Sites.kt` → `SiteEditor`؛ التحليل → `engine/SiteProfiler.kt` (`ProfileLogic` نقية)
- Room v4 + ترحيلات (site_id، quality، remote_url) → `data/Db.kt`، `data/Entities.kt` → `Migrations`
- المفاتيح/المزودون → `data/{KeyBackup,Providers,SecretStore,Stores}.kt`؛ التوليد → `engine/{Generator,JobRunner}.kt`؛ GitHub → `engine/GitHubClient.kt`
- فهرس المدونة → `engine/BlogIndex.kt`؛ الأخبار → `engine/NewsSources.kt`؛ المكرر: حالة `duplicate` (مستثناة لا فاشلة) في `JobRunner`
- XML بلوجر، التصدير، التسمية، الخدمة الأمامية → `engine/{XmlBuilder,ExportManager,ExportNaming,GenerationService}.kt`

## Decisions
- كل موقع مستقل بمفتاح `siteId`؛ `default` = سبورت لايف 24 ويحتفظ بقائمة `seed`.
- كل مقال يُحفظ في Room فور اكتماله؛ المهمة تستأنف من `jobs.next` بعد أي توقف.
- الصور بإيداع واحد لفرع `blog-images`، روابط jsDelivr بمعرّف الإيداع.
- رقم البناء = `GITHUB_RUN_NUMBER`؛ التاج `vX.Y.Z-bN`.

## Solved bugs (never reintroduce)
- تعليق التنقل: لا تعد `observeAll` ولا فك التشفير خارج `SecretStore.cache`.
- `SchemaRules.sanitize` كان يقص المسافات أثناء الكتابة؛ في الحقول استعمل `trim = false`.
- تداخل الواجهة مع أشرطة النظام (targetSdk 36 يفرض edge-to-edge): استعمل `EdgeBar` لا حشواً متفرقاً.
- وصف RSS مهرّب (`&lt;p&gt;`): فك الكيانات قبل إزالة الوسوم ثم بعدها. `NewsSources.clean`.
- الأرقام معكوسة في الصور: استعمل `drawText` مع RLM (`ImageRenderer.textR`).
- 7 رموز (bulb، heart…) كانت تسقط لأن `stroke="url(#gf)"` لم يُدعم؛ `MotifTest` يغطي كل الرموز.
- توقيع الإصدار: `keystorePath` من البيئة لا `storeFile` داخل `create("release")`.

- مراحل التصدير: 4 دون نشر، 6 معه (`ExportManager.publishStages`: تأكيد الصور بـ HEAD على غلاف الأول والأخير ثم النشر)؛ `retryPublish` يكمل المتوقف.
- النشر المباشر يمر عبر XML نفسه (`XmlImport.parse`) فمسار التصدير والملف اليدوي واحد؛ روابط «اقرأ أيضاً» لمقالات الدفعة تُحذف لأن بلوجر يولّد الرابط؛ الـ API لا يضبط permalink ولا metaDescription.
- لا جسر Apps Script ولا نشر بالبريد: لا خدمة Blogger في Apps Script، والبريد بلا تصنيفات/رابط راجع/جدولة. الجدولة = مسودة ثم `publish?publishDate`.
- قوائم Room تقرأ `ArticleRow` (بلا JSON)؛ الجودة عمود `quality`.

## Open issues
- المؤقت: WorkManager دوري (ساعة كحد أدنى) قد يتأخر مع Doze؛ العامل ≤10 دقائق فتُستأنف المهمة المقطوعة في الدورة التالية. لم يُجرَّب على جهاز.
- لم يُجرَّب: سلوك `published` المستقبلي في الجدولة، وبقاء <script> JSON-LD في المحتوى.
- لم يُجرَّب على جهاز: الخدمة الأمامية، Keystore، مزودات حقيقية، ESPN.
