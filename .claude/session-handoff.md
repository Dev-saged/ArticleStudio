# session-handoff
- الحالة: v0.12.0 (b40) على main، CI أخضر. فرع `docs/readme-media`: README جديد + وسائط.
- أُنجز: SVG (hero, features, control-room, architecture, providers)، social-card.png، `promo.mp4` (60ث، 1080×1920، صوت Gemini TTS) و`promo-poster.jpg`.
- الصوت: `tools/promo/voice.py` يقرأ `GEMINI_API_KEY` من البيئة فقط؛ حدّ الطلبات 429 يعالجه الانتظار والبديل. المفتاح لُصق في المحادثة، ينبغي تدويره.
- التسلسل: `render.py` ← `voice.py` ← `mux.py <voiceDir> <silent> <out>`.
- قرارات: لا رفع نسخة تطبيق لتغييرات docs فقط.
- لم يُتحقق: جودة الصوت بالسمع، مصادر حية، جهاز حقيقي.
- التالي: دمج PR ثم اختبار الأجهزة الفعلية للتوازي والمزودين.
