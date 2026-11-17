<div align="center">

<img src="docs/media/hero.svg" alt="استوديو المقالات" width="100%"/>

**اكتب · صمّم · انشر — من هاتفك**

[![Build APK](https://github.com/Dev-saged/ArticleStudio/actions/workflows/build.yml/badge.svg)](https://github.com/Dev-saged/ArticleStudio/actions/workflows/build.yml)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7f52ff)

[![شاهد الفيديو التعريفي](docs/media/promo-poster.jpg)](docs/media/promo.mp4)

▶ [الفيديو التعريفي (دقيقة، بصوت عربي)](docs/media/promo.mp4)

</div>

## الفكرة
تطبيق Android أصلي يدير عدة مواقع بلوجر من الهاتف: يولّد المقالات والأغلفة بالذكاء الاصطناعي، يصدّرها، ويرفع الصور وينشر مباشرة، دون حاسوب.

<img src="docs/media/features.svg" alt="المميزات" width="100%"/>

## غرفة التحكم
الشاشة الرئيسية تعرض كل المواقع معاً: تقدّم كل موقع، عدد الجاهز والمنشور، وتشغيل أكثر من موقع بالتوازي، كل موقع بمهامه المستقلة، مع حرية التنقل أثناء العمل.

<img src="docs/media/control-room.svg" alt="غرفة التحكم" width="100%"/>

## لا يتوقف أبداً
مزودو نص وصور متعددون بتبديل تلقائي: عند التعثّر أو انتهاء الحصة يُجمَّد المصدر مؤقتاً ويُستكمل العمل بالتالي، وآخر الحلقة رسم SVG محلي مضمون.

<img src="docs/media/providers.svg" alt="المزودون" width="100%"/>

## المعمارية
<img src="docs/media/architecture.svg" alt="المعمارية" width="100%"/>

## القدرات
- **توليد المقالات:** مجال كل موقع، أرقام موثّقة، منع الاقتباسات المخترعة والتكرار.
- **الأغلفة:** صور واقعية من مصادر متعددة، وبطاقات معلومات بهوية كل موقع.
- **التوازي:** عدة مواقع في وقت واحد بحالة مستقلة لكل موقع.
- **التصدير والنشر:** XML لبلوجر، رفع الصور إلى GitHub، نشر عبر Blogger API، واستكمال المنشورات المتوقفة بزر واحد.
- **العمل دون اتصال:** كل طلب شبكة محمي بإعادة محاولة وبديل آمن.
- **عربي RTL بالكامل** مع أرقام جدولية وأيقونات متجهة مرسومة يدوياً.

## التقنيات
Kotlin · Jetpack Compose · Hilt + KSP · Room (ترحيلات مرقّمة) · OkHttp · kotlinx.serialization · GitHub Actions.

## مواد للنشر
صورة جاهزة لإنستغرام وفيسبوك (1080×1350): [`docs/media/social-card.png`](docs/media/social-card.png)

## البناء والتوقيع
كل push يبني APK عبر `build.yml`. لنشر Release موقّع أضف في Settings ← Secrets and variables ← Actions: `KEYSTORE_BASE64` و`SIGNING_STORE_PASSWORD` و`SIGNING_KEY_ALIAS` و`SIGNING_KEY_PASSWORD`؛ بدونها يُبنى APK بمفتاح مؤقت بلا Release.

## بنية المستودع
- `app/` تطبيق Android · `docs/` الخريطة وملاحظات الإصدارات والوسائط
- `tools/media/` يولّد صور SVG: `python3 build_svgs.py`
- `tools/promo/` الفيديو: `render.py` (إطارات) ثم `voice.py` (صوت Gemini TTS من متغير `GEMINI_API_KEY`) ثم `mux.py`
- القواعد في `CLAUDE.md` والخريطة في `docs/repo-map.md`
