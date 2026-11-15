# code-index (يُولَّد تلقائياً — لا تقرأه كاملاً)

> الاستعمال: `Grep "اسم_الرمز" docs/code-index.md` ثم Read بنطاق أسطر من الملف الناتج.

<!-- repomap:auto:start (tools/repomap.py — لا تعدّل يدوياً) -->
## Code index (auto)
> 72 ملفاً · 5552 سطراً. للتفاصيل: Grep باسم الرمز ثم Read بنطاق أسطر، لا تقرأ الملفات الكبيرة كاملة.
> الأكبر: ProviderEngine.kt 260 · Generator.kt 252 · Providers.kt 211 · HomeScreen.kt 198 · Articles.kt 191 · 2.json 181 · Export.kt 168 · repomap.py 146

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
- build.gradle.kts (81)
### app/schemas/com.sajed.articlestudio.data.AppDb/
- 1.json (139)
- 2.json (181)
### app/src/main/
- AndroidManifest.xml (40)
### app/src/main/assets/
- studio-data.json (1)
### app/src/main/kotlin/com/sajed/articlestudio/
- App.kt (8): App
- BrandConfig.kt (41): BrandConfig
- MainActivity.kt (45): MainActivity
### app/src/main/kotlin/com/sajed/articlestudio/data/
- Db.kt (72): ArticleDao, JobDao, Migrations, AppDb
- Entities.kt (39): ArticleEntity, JobEntity
- Providers.kt (74): ProviderSpec, ProviderCatalog, ProviderStore
- SecretStore.kt (63): SecretException, SecretStore
- Stores.kt (71): Health, HealthStore, History, HistoryStore, GitHubSettings
### app/src/main/kotlin/com/sajed/articlestudio/di/
- Modules.kt (29): DataModule
### app/src/main/kotlin/com/sajed/articlestudio/engine/
- ExportManager.kt (109): ExportStep, ExportUi, ExportManager
- GenerationService.kt (102): GenerationService
- Generator.kt (252): PlanMode, Generator
- GitHubClient.kt (126): GitHubException, ImageFile, GitHubClient
- ImageRenderer.kt (140): RenderedImages, ImageRenderer
- JobRunner.kt (144): JobUi, JobRunner
- Models.kt (34): Ref, Topic, Fact, Section, Faq, Article
- ProviderEngine.kt (260): ApiException, ChatMessage, ChatOpts, ChatResult, ProviderStat, Call.await, ProviderEngine
- RunBus.kt (46): LogLine, PassEvent, RunBus
- StudioData.kt (19): StudioData
- SvgDraw.kt (129): hsl, SvgDraw
- Text.kt (61): esc, toHtml, Tag, parseTags, one, List, wc +6
- XmlBuilder.kt (83): XmlBuilder
### app/src/main/kotlin/com/sajed/articlestudio/schema/
- Engine.kt (113): SchemaForm, KpiRow, SchemaRow
- Schema.kt (115): FieldType, Field, Kpi, ModuleSchema, SchemaRules, Schemas
### app/src/main/kotlin/com/sajed/articlestudio/ui/
- Components.kt (117): Modifier.focusRing, IconBtn, PrimaryButton, AppChip, Panel, LiveBar
- Motion.kt (55): Modifier.stagger, AmbientBackground
- Prefs.kt (33): UiPrefs, LiveStatus
### app/src/main/kotlin/com/sajed/articlestudio/ui/home/
- HomeScreen.kt (198): HomeScreen
- HomeViewModel.kt (130): NodeState, NodeUi, BallPass, RowUi, HomeUiState, HomeViewModel
- PitchBoard.kt (145): PitchBoard
### app/src/main/kotlin/com/sajed/articlestudio/ui/icons/
- Icons.kt (53): Icons
### app/src/main/kotlin/com/sajed/articlestudio/ui/nav/
- AppNav.kt (111): AppNav
### app/src/main/kotlin/com/sajed/articlestudio/ui/screens/
- Articles.kt (191): ArticleItem, ArticlesViewModel, ArticlesScreen, EmptyState
- Export.kt (168): ExportViewModel, ExportScreen
- Providers.kt (211): ProviderUi, ProvidersViewModel, ProvidersScreen
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
- file_paths.xml (5)
### app/src/test/kotlin/com/sajed/articlestudio/
- CoreTest.kt (39): CoreTest
- MigrationTest.kt (31): MigrationTest
### app/src/test/kotlin/com/sajed/articlestudio/engine/
- EngineTest.kt (83): EngineTest
- Fixtures.kt (14): sampleArticle
- GeneratorTest.kt (106): GeneratorTest
- JobRunnerTest.kt (121): JobRunnerTest
- ProviderHttpTest.kt (85): ProviderHttpTest
- RenderTest.kt (38): RenderTest
### docs/
- repo-map.md (43)
### tools/
- repomap.py (146): files, symbols, build, main
<!-- repomap:auto:end -->
