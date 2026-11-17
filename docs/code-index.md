# code-index (يُولَّد تلقائياً — لا تقرأه كاملاً)

> الاستعمال: `Grep "اسم_الرمز" docs/code-index.md` ثم Read بنطاق أسطر من الملف الناتج.

<!-- repomap:auto:start (tools/repomap.py — لا تعدّل يدوياً) -->
## Code index (auto)
> 105 ملفاً · 10090 سطراً. للتفاصيل: Grep باسم الرمز ثم Read بنطاق أسطر، لا تقرأ الملفات الكبيرة كاملة.
> الأكبر: Sites.kt 448 · Export.kt 338 · ImageRenderer.kt 322 · ProviderEngine.kt 295 · Providers.kt 279 · Generator.kt 249 · HomeScreen.kt 246 · Publish.kt 237

### ./
- CLAUDE.md (44)
- README.md (7)
- build.gradle.kts (9)
- settings.gradle.kts (10)
- version.json (5)
### .github/workflows/
- apply-to-all-repos.yml (106)
- build.yml (104)
- new-project.yml (95)
- repomap.yml (41)
- unpack-zip.yml (79)
### app/
- build.gradle.kts (83)
### app/schemas/com.sajed.articlestudio.data.AppDb/
- 1.json (139)
- 2.json (181)
- 3.json (202)
- 4.json (209)
### app/src/main/
- AndroidManifest.xml (41)
### app/src/main/assets/
- studio-data.json (1)
### app/src/main/kotlin/com/sajed/articlestudio/
- App.kt (27): App
- BrandConfig.kt (40): BrandConfig
- MainActivity.kt (45): MainActivity
### app/src/main/kotlin/com/sajed/articlestudio/data/
- Db.kt (117): ArticleRow, ArticleDao, JobDao, Migrations, AppDb
- Entities.kt (43): ArticleEntity, JobEntity
- KeyBackup.kt (154): BackupEntry, BackupGit, Backup, ImportReport, KeyBackup
- Providers.kt (64): ProviderSpec, ProviderCatalog, ProviderStore
- SecretStore.kt (66): SecretException, SecretStore
- Stores.kt (72): Health, HealthStore, History, HistoryStore, GitHubSettings
### app/src/main/kotlin/com/sajed/articlestudio/di/
- Modules.kt (29): DataModule
### app/src/main/kotlin/com/sajed/articlestudio/engine/
- AutoPipeline.kt (54): interface, AutoPipeline, PipelineModule
- AutoRun.kt (95): AutoPlan, AutoRunner, AutoScheduler, AutoEntry, AutoWorker
- BlogIndex.kt (81): BlogPost, BlogIndex
- ExportManager.kt (232): ExportSummary, ExportFile, ExportUi, ExportManager
- ExportNaming.kt (28): ExportNaming
- GenerationService.kt (102): GenerationService
- Generator.kt (249): PlanMode, OffTopicException, Generator
- GitHubClient.kt (124): GitHubException, ImageFile, GitHubClient
- GoogleAuth.kt (53): GoogleAuth
- Guards.kt (82): NicheGuard, FactGuard, toneGuide
- ImageEngine.kt (206): ImgReq, ImgHit, ImgStat, ImageEngine
- ImageRenderer.kt (322): RenderedImages, ImageRenderer
- JobRunner.kt (174): JobUi, JobRunner
- Models.kt (37): Ref, Topic, Fact, Section, Faq, Article
- NewsSources.kt (105): Feed, feedsFor, NewsSources
- PhotoService.kt (90): PhotoPrompt, PhotoService
- ProviderEngine.kt (295): ApiException, ChatMessage, ChatOpts, ChatResult, ProviderStat, ValidationFail, friendlyApi +2
- PublishManager.kt (79): PublishReport, PublishEntry, PublishManager
- Publisher.kt (123): PubState, PublishItem, Remote, PublishException, BlogPublisher, withRetry, GooglePublisher
- RunBus.kt (46): LogLine, PassEvent, RunBus
- SigningInfo.kt (21): SigningInfo
- Site.kt (181): FeedDef, Niche, Niches, SiteConfig, SiteRules, SiteStore
- SiteIndex.kt (36): IndexItem, SiteIndexDoc, SiteIndex
- SiteProfiler.kt (130): BlogSnapshot, SiteProfile, SiteProfiler, ProfileLogic
- StudioData.kt (19): StudioData
- SvgDraw.kt (131): hsl, SvgDraw
- Text.kt (89): esc, toHtml, Tag, parseTags, one, List, wc +9
- Updater.kt (140): UpdateInfo, UpdatePhase, UpdateUi, Updater
- XmlBuilder.kt (110): XmlBuilder
- XmlImport.kt (42): XmlImport
### app/src/main/kotlin/com/sajed/articlestudio/schema/
- Engine.kt (113): SchemaForm, KpiRow, SchemaRow
- Schema.kt (117): FieldType, Field, Kpi, ModuleSchema, SchemaRules, Schemas
### app/src/main/kotlin/com/sajed/articlestudio/ui/
- Components.kt (117): Modifier.focusRing, IconBtn, PrimaryButton, AppChip, Panel, LiveBar
- Motion.kt (55): Modifier.stagger, AmbientBackground
- Prefs.kt (33): UiPrefs, LiveStatus
- UpdateCard.kt (93): UpdateCard
### app/src/main/kotlin/com/sajed/articlestudio/ui/home/
- HomeScreen.kt (246): HomeScreen
- HomeViewModel.kt (139): NodeState, NodeUi, BallPass, RowUi, HomeUiState, HomeViewModel
- PitchBoard.kt (145): PitchBoard
### app/src/main/kotlin/com/sajed/articlestudio/ui/icons/
- Icons.kt (55): Icons
### app/src/main/kotlin/com/sajed/articlestudio/ui/nav/
- AppNav.kt (137): AppNav
### app/src/main/kotlin/com/sajed/articlestudio/ui/screens/
- Articles.kt (203): ArticleItem, ArticlesViewModel, ArticlesScreen, EmptyState
- Export.kt (338): ExportViewModel, ExportScreen, pubModeLabel
- Providers.kt (279): ProviderUi, ProvidersViewModel, ProvidersScreen
- Publish.kt (237): GuideStep, PublishGuide, TestUi, PublishViewModel, PublishSheet
- Sites.kt (448): SiteStats, SiteForms, SitesViewModel, SitesScreen
### app/src/main/kotlin/com/sajed/articlestudio/ui/test/
- SelfTestScreen.kt (116): SelfTestScreen
### app/src/main/kotlin/com/sajed/articlestudio/ui/theme/
- Oklch.kt (27): Oklch
- Theme.kt (73): ThemeMode, AppColors, AppTheme, rememberReduceMotion, ArticleStudioTheme
- Type.kt (40): String.ltr
### app/src/main/res/drawable/
- ic_launcher_bg.xml (6)
- ic_launcher_fg.xml (11)
- ic_launcher_mono.xml (11)
- ic_stat.xml (8)
### app/src/main/res/mipmap-anydpi-v26/
- ic_launcher.xml (7)
### app/src/main/res/values/
- strings.xml (4)
- themes.xml (9)
### app/src/main/res/xml/
- file_paths.xml (6)
### app/src/test/kotlin/com/sajed/articlestudio/
- CoreTest.kt (39): CoreTest
- MigrationTest.kt (51): MigrationTest
### app/src/test/kotlin/com/sajed/articlestudio/engine/
- AutoRunTest.kt (40): AutoRunTest
- EngineTest.kt (83): EngineTest
- Fixtures.kt (14): sampleArticle
- GeneratorTest.kt (106): GeneratorTest
- GuardsTest.kt (52): GuardsTest
- ImageEngineTest.kt (138): ImageEngineTest
- JobRunnerTest.kt (148): JobRunnerTest
- MotifTest.kt (22): MotifTest
- PhotoServiceTest.kt (99): PhotoServiceTest
- ProviderHttpTest.kt (100): ProviderHttpTest
- PublishTest.kt (112): PublishTest
- RenderTest.kt (58): RenderTest
- SiteProfilerTest.kt (51): SiteProfilerTest
- SiteTest.kt (94): SiteTest
- V3Test.kt (114): V3Test
### docs/
- publishing.md (20)
- repo-map.md (41)
### tools/
- repomap.py (146): files, symbols, build, main
<!-- repomap:auto:end -->
