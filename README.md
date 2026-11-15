
# ArticleStudio
تطبيق Android أصلي (Kotlin + Compose) يولّد مقالات بلوجر عبر مزودين مجانيين يتبدلون تلقائياً. القواعد في `CLAUDE.md` والخريطة في `docs/repo-map.md`.

## البناء والتوقيع
كل push يبني APK عبر `build.yml`. لنشر Release موقّع أضف في Settings ← Secrets and variables ← Actions: `KEYSTORE_BASE64` و`SIGNING_STORE_PASSWORD` و`SIGNING_KEY_ALIAS` و`SIGNING_KEY_PASSWORD`؛ بدونها يُبنى APK بمفتاح مؤقت بلا Release.
