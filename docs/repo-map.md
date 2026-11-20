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
- Room v5 + ترحيلات (…، reviews) → `data/{Db,Entities}.kt`؛ المراجعة/الإحصاء/النسخة → `engine/{Review,Stats,Backup}.kt`، الواجهة `ui/screens/Insights.kt` (مسار `insights`)
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
- أحمر المزودين: `ValidationFail` لا يمس `stat`؛ `ProviderStat.hard` (401/403 فقط)؛ LLM7 يحتاج `Bearer unused`.
- توقيع الإصدار: `keystorePath` من البيئة لا `storeFile` داخل `create("release")`.

- صور: `ProviderCatalog` بـ`kind="image"` ومفاتيحها `key.<id>`؛ Cloudflare: `ACCOUNT_ID:TOKEN`.
- مهلات الشبكة: `DataModule.http` قراءة 60ث/كتابة 90ث؛ `GitHubClient.gh` يعيد 3 مرات للشبكة/5xx/429. سبب «timeout» القديم: مهلة 10ث الافتراضية.
- المتوقف عن النشر: `ExportManager.publishPending` (recheck)، زر «انشر المتوقف».
- `JobRunner`: مهمة مستقلة لكل موقع (`runs[siteId]`)؛ `states`/`stateOf(id)`، `state` مجمّع؛ `Gate` (حد `GenSettings.parallel` 1..4)؛ `findByTitle` يمنع تكرار النشر عند إعادة المحاولة؛ `perSlot` للجدولة؛ `resume(siteId)`، `stop(siteId?)`.

- مشاركة اجتماعية: `social/` (`ShareStore.enqueue` بعد `setRemote`، `ShareRunner.tick`، `ShareWorker`)؛ حسابات api (تيليغرام/بلوسكي بـ`SecretStore soc.<id>`) أو ويب (جلسة WebView لكل حساب، `WebAutomator` + `assets/social/recipes.json` تُحدَّث من GitHub). Room v6: `social_accounts`,`share_jobs`. شاشة `Social.kt`.
- مشاركة: المهمة `running` المقطوعة تُركن `needs_you` ولا تُعاد (منعاً لنشر مكرر)؛ `login_required` يوقف الحساب؛ `ShareEntry` بأسماء `share*` لتفادي تعارض Hilt مع `AutoEntry.runner`.

- مشاركة v0.14.1: تحقق جلسة الويب بالكوكيز (`WebAutomator.hasSession` + `PlatformSpec.cookies`: x auth_token، facebook c_user+xs، threads sessionid، linkedin li_at) لا بفتح صفحة الدخول (كان regex الدخول يطابق رابط الفحص نفسه فتظهر «الجلسة منتهية» دائماً).
- مشاركة: تيليغرام `tgFail` (401 توكن، 403/400 `rejected` لا `login_required`)، `check` يتحقق من getChat وصلاحية المشرف؛ `ShareStore.catchUp` (قديم غير مسجَّل، الأقدم أولاً ضمن السقف اليومي، مفتاح `prefs social/backfill`)، `shareNow`، `setCredentials`؛ حالة حساب `attention`؛ `SiteConfig.share` افتراضي true.

- مشاركة v0.15.0: خزنة كوكيز مشفرة لكل حساب ويب (`WebAutomator.saveSession/restoreSession`، مفتاح `soc.cookies.<id>`): تُحدَّث عند النجاح وتُستعاد قبل كل فحص/نشر (كوكيز الجلسة غير الدائمة كانت تضيع)؛ `getOrCreateProfile` بدل `getProfile`؛ `resumeTimers` للـWebView المنفصل؛ ظهور صفحة دخول مع وجود الكوكيز يُعاد مرة ثم `ui_changed` (لا يوقف الحساب).
- إنستغرام: منصة ويب `needsImage` (وصفة `instagram` في recipes.json، عملية `attach` في automation.js ترفع الصورة base64 عبر DataTransfer). `ShareMedia` (غلاف المقال من `…-cover.jpg` في `ArticleEntity.html` ثم og:image) يُنزَّل ويُصغَّر 1080px في `filesDir/share-media/<id>.jpg` ويُحذف بعد 3 أيام (`MediaRules.KEEP_MS`، يُستدعى من `ShareRunner.tick`).

## Open issues
- لم تُجرَّب حياً وصفات فيسبوك/X/ثريدز/لينكدإن (محددات تخمينية + `recipe doctor` يسجل أزرار الصفحة عند الفشل).
- لم يُجرَّب حياً: مصادر الصور المفتاحية وGitHub Models (MockWebServer فقط).
- Media: `tools/media` (SVG) ؛ الفيديو من sajed-brain/video-identity/film.
