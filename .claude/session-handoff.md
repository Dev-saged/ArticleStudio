# session-handoff

## الحالة
- v0.11.0 على فرع `feature/multi-sources`: مصادر صور متعددة (`ImageEngine`) + مزودا نص (GitHub Models، Together) + بطاقات المصادر في شاشة المزودين.
- الاختبارات: 89 خضراء، `assembleDebug` ناجح محلياً.

## قرارات
- ترتيب الصور: gemini، together، img-cloudflare، img-pollinations، img-pexels، img-pixabay.
- `PhotoService.fetch` يمرّر القبول (`decode`) للمحرك؛ الرفض = إخفاق المصدر.
- مفاتيح الصور في `key.<id>`؛ Gemini وTogether يشاركان مفتاح النص.

## مفتوح
- لم يُجرَّب حياً: أي مصدر صور مدفوع/مفتاحي، ولا GitHub Models، ولا أسماء نماذج Gemini للصور.
- المؤقت الدوري لم يُجرَّب على جهاز.

## الخطوة التالية
- دمج الـPR، ثم تجربة المفاتيح على الجهاز من شاشة المزودين (زر اختبار) وتعديل أسماء النماذج إن لزم.
