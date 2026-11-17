# repo-map — ArticleStudio

> أول ما تفتح: هذا الملف ثم `.claude/session-handoff.md`. ابحث عن الرموز بـ `Grep` في `docs/code-index.md` (يُولَّد تلقائياً) ولا تقرأ ملفاً فوق 300 سطر كاملاً.

## Quick index (ميزة → ملف → ابحث بـ)
- الهوية والألوان → `BrandConfig.kt` → `BrandConfig`
- الثيم، المكونات، الحركة، الأيقونات → `ui/theme/*`، `ui/Components.kt`، `ui/Motion.kt`، `ui/icons/Icons.kt`
- الحرّاس → `engine/Guards.kt` (`NicheGuard` مجال، `FactGuard` أرقام/اقتباسات، `toneGuide`)؛ الصور الواقعية → `engine/PhotoService.kt` فوق `engine/ImageEngine.kt` (سلسلة: Gemini، Together، Cloudflare، Pollinations، Pexels، Pixabay؛ تجميد وإحصاءات؛ احتياط SVG)؛ فهرس الموقع → `engine/SiteIndex.kt`
- المخططات → `schema/*`؛ الرسم → `engine/ImageRenderer.kt`؛ التحديث → `engine/Updater.kt`؛ الشاشات → `ui/screens/*`
- الرئيسية = غرفة التحكم: بطاقات المواقع (`SiteCard`) ثم غرفة الموقع → `ui/home/*` → `HomeViewModel` (`openRoom`، `quick`)، `PitchBoard` ثابتة لكل المواقع
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

## Solved bugs (never reintroduce)
- `SchemaRules.sanitize` كان يقص المسافات أثناء الكتابة؛ في الحقول استعمل `trim = false`.
- تداخل الواجهة مع الأشرطة: استعمل `EdgeBar` (`ui/nav/AppNav.kt`).
- الأرقام معكوسة في الصور: استعمل `drawText` مع RLM (`ImageRenderer.textR`).
- مقالات فاشلة كانت بلا `siteId` فتظهر في سبورت لايف؛ كل `ArticleEntity(` يجب أن يحمل `siteId`، و`start` بلا افتراضي.
- أحمر كل المزودين: رمي `validate` (JSON التعبئة) كان يُحسب إخفاقاً على المزود. الآن `ValidationFail` لا يمس `stat`؛ والرسائل عبر `friendlyApi`؛ `ProviderStat.hard` للأحمر (401/403 فقط). LLM7 يحتاج `Bearer unused`.
- توقيع الإصدار: `keystorePath` من البيئة لا `storeFile` داخل `create("release")`.

- مصادر الصور مواصفاتها في `ProviderCatalog` بـ`kind="image"` ومفاتيحها `key.<id>`؛ Gemini وTogether يعيدان استعمال مفتاح النص. Cloudflare: `ACCOUNT_ID:TOKEN`.
- مهلات الشبكة: `DataModule.http` قراءة 60ث/كتابة 90ث؛ `GitHubClient.gh` يعيد 3 مرات للشبكة/5xx/429. سبب «timeout» القديم: مهلة 10ث الافتراضية.
- المتوقف عن النشر: `ExportManager.publishPending` (من XML المحفوظ)، زر «انشر المتوقف» في Export.
- `JobRunner`: مهمة مستقلة لكل موقع (`runs[siteId]`)؛ `states`/`stateOf(id)`، `state` مجمّع؛ `Semaphore(3)` يحد مقالات LLM المتزامنة؛ `resume(siteId)`، `stop(siteId?)`.

## Open issues
- لم يُجرَّب حياً: مصادر الصور المفتاحية وGitHub Models (MockWebServer فقط).
- لم يُجرَّب: جهاز، مزودات حقيقية، Gemini للصور (مسار غير مُتحقَّق).
