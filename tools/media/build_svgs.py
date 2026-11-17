from common import *

# ---------- hero ----------
def hero():
    W, H = 1280, 640
    b = backdrop(W, H)
    b += mark(1120, 78, 96)
    b += t(1100, 150, "استوديو المقالات", 80, C["text"], 800, extra='')
    b += t(1176, 232, "اكتب وصمّم وانشر لعدة مواقع بلوجر من هاتفك", 34, C["gold"], 800)
    b += t(1176, 284, "ذكاء اصطناعي بمزودين متعددين يتبدلون تلقائياً، أغلفة واقعية،", 24, C["mute"], 400)
    b += t(1176, 320, "ونشر مباشر بلا حاجة إلى حاسوب.", 24, C["mute"], 400)
    x = 1176
    for lab, hot in (("مزودون مجانيون", True), ("أغلفة واقعية", False), ("نشر مباشر", False), ("عدة مواقع بالتوازي", False)):
        s, w = pill(x, 372, lab, 21, fill="#1b2542" if not hot else None, stroke=C["gold"] if hot else C["line"], color=C["gold"] if hot else C["text"])
        b += s; x -= w + 12
    b += t(1176, 470, "Kotlin · Jetpack Compose · Room · Hilt", 20, C["mute"], 400, extra='unicode-bidi="isolate"', rtl=False)
    b += t(1176, 506, "أندرويد أصلي · واجهة عربية RTL · يعمل دون اتصال", 20, C["mute"], 400)
    b += phone(90, 18, 1.0)
    return svg(W, H, b, BG_DEFS)

# ---------- control room ----------
def control_room():
    W, H = 1280, 760
    b = backdrop(W, H)
    b += t(1200, 92, "غرفة التحكم", 60, C["text"], 800)
    b += t(1200, 138, "كل مواقعك أمامك، وكل موقع بمهمته المستقلة", 25, C["gold"], 800)
    notes = [("بطاقات حية", "المرحلة والتقدم وعدد الجاهز والمنشور لكل موقع."),
             ("توليد متوازٍ", "أربعة مقالات لموقع وخمسة لآخر في اللحظة نفسها."),
             ("تنقل حر", "ادخل أي موقع أو اخرج منه دون إيقاف عمل غيره."),
             ("لا شيء يتوقف", "المتوقف عن النشر يُستأنف من ملفاتك بضغطة.")]
    y = 220
    for h1, p in notes:
        b += rect(660, y, 540, 112, 20, fill=C["surface"], stroke=C["line"])
        b += rect(1170, y + 24, 8, 64, 4, fill="url(#gold)")
        b += t(1148, y + 52, h1, 28, C["text"], 800)
        b += t(1148, y + 88, p, 19, C["mute"], 400)
        y += 128
    b += phone(250, 90, 1.12)
    return svg(W, H, b, BG_DEFS)

# ---------- architecture ----------
def architecture():
    W, H = 1280, 520
    b = backdrop(W, H)
    b += t(1220, 80, "من الفكرة إلى النشر", 48, C["text"], 800)
    b += t(1220, 120, "مسار واحد، وكل حلقة فيه لها بديل احتياطي", 22, C["mute"], 400)
    steps = [("المواضيع", "أخبار حية أو مواضيعك"), ("سلسلة المزودين", "تبديل تلقائي وتجميد مؤقت"),
             ("حواجز الدقة", "مجال الموقع وأرقام موثّقة"), ("الأغلفة", "صورة واقعية أو رسم SVG"),
             ("التصدير والرفع", "XML وصور على GitHub"), ("بلوجر", "نشر مباشر أو مسودة")]
    bw, gap = 176, 24
    x = 1220 - bw
    for i, (h1, p) in enumerate(steps):
        b += rect(x, 190, bw, 200, 24, fill=C["surface"], stroke=C["gold"] if i in (1, 5) else C["line"], sw=2)
        b += f'<circle cx="{x + bw / 2}" cy="236" r="22" fill="{C["gold"] if i in (1, 5) else "#26335a"}"/>'
        b += t(x + bw / 2, 244, str(i + 1), 22, "#1b1204" if i in (1, 5) else C["text"], 800, "middle")
        b += t(x + bw / 2, 300, h1, 22, C["text"], 800, "middle")
        # التفاف نص الوصف
        words = p.split(); lines = []; cur = ""
        for w_ in words:
            if len(cur + " " + w_) > 15 and cur: lines.append(cur); cur = w_
            else: cur = (cur + " " + w_).strip()
        lines.append(cur)
        for j, ln in enumerate(lines[:3]): b += t(x + bw / 2, 336 + j * 24, ln, 17, C["mute"], 400, "middle")
        if i < len(steps) - 1:
            ax = x - gap / 2
            b += f'<path d="M{ax + 7} 290 L{ax - 7} 290 M{ax - 1} 283 L{ax - 8} 290 L{ax - 1} 297" fill="none" stroke="{C["gold"]}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>'
        x -= bw + gap
    b += rect(60, 430, 1160, 52, 26, fill="#101830", stroke=C["line"])
    b += t(640, 464, "كل موقع له مقالاته وفهرسه ومجلد تصديره المستقل، فلا يختلط محتوى موقع بآخر", 22, C["text"], 700, "middle")
    return svg(W, H, b, BG_DEFS)

# ---------- providers ----------
def providers():
    W, H = 1280, 560
    b = backdrop(W, H)
    b += t(1220, 80, "لا يتوقف أبداً", 52, C["text"], 800)
    b += t(1220, 122, "إذا تعثّر مصدر انتقل النظام للتالي فوراً وجمّد المتعثر مؤقتاً", 22, C["mute"], 400)
    def chain(xr, title, items, hot):
        o = rect(xr - 560, 170, 560, 340, 28, fill=C["surface"], stroke=C["line"])
        o += t(xr - 28, 220, title, 28, C["gold"], 800)
        x = xr - 28; y = 262
        for it in items:
            s, w = pill(x, y, it, 19, fill="#1b2542")
            if x - w < xr - 540: x = xr - 28; y += 58; s, w = pill(x, y, it, 19, fill="#1b2542")
            o += s; x -= w + 10
        return o
    b += chain(1220, "نصوص المقالات", ["Gemini", "Groq", "Cerebras", "OpenRouter", "GitHub Models", "Together", "Mistral", "SambaNova", "NVIDIA NIM", "Cohere", "LongCat", "LLM7"], True)
    b += chain(640, "صور الأغلفة", ["Gemini", "Together FLUX", "Cloudflare FLUX", "Pollinations", "Pexels", "Pixabay", "رسم SVG احتياطي"], False)
    b += f'<path d="M1220 530 H80" stroke="url(#gold)" stroke-width="3" stroke-dasharray="2 12" stroke-linecap="round"/>'
    b += t(640, 548, "إحصاءات نجاح وإخفاق لكل مصدر · الأحمر لرفض المفتاح فقط", 18, C["mute"], 400, "middle")
    return svg(W, H, b, BG_DEFS)

# ---------- features ----------
def features():
    W, H = 1280, 760
    b = backdrop(W, H)
    b += t(1220, 90, "ما الذي يقدّمه؟", 56, C["text"], 800)
    cards = [("مقالات بقواعد صارمة", "مجال كل موقع ونبرته وجمهوره، ومنع الأرقام غير المسندة والاقتباسات المخترعة."),
             ("أغلفة وبطاقات", "صورة غلاف واقعية، وبطاقة معلومات، ولوحة القناة بهوية الموقع."),
             ("نشر بلا حاسوب", "رفع الصور إلى GitHub وتصدير XML أو نشر مباشر عبر Blogger API."),
             ("عدة مواقع بعزل تام", "فهرس ومجلد وحالة مستقلة لكل موقع، وتعرّف تلقائي على مجال الموقع."),
             ("يتجنب التكرار", "يقرأ مقالات مدونتك المنشورة ويستثني ما يشبهها قبل الكتابة وبعدها."),
             ("جدولة وتحديث", "توليد تلقائي بمواعيد، وتحديث التطبيق من إصدارات GitHub.")]
    for i, (h1, p) in enumerate(cards):
        col, row = i % 3, i // 3
        x = 1220 - 380 - col * 400; y = 150 + row * 290
        b += rect(x, y, 380, 262, 26, fill=C["surface"], stroke=C["line"])
        b += rect(x + 316, y + 28, 36, 8, 4, fill="url(#gold)")
        b += t(x + 352, y + 86, h1, 27, C["text"], 800)
        words = p.split(); lines = []; cur = ""
        for w_ in words:
            if len(cur + " " + w_) > 29 and cur: lines.append(cur); cur = w_
            else: cur = (cur + " " + w_).strip()
        lines.append(cur)
        for j, ln in enumerate(lines[:4]): b += t(x + 352, y + 132 + j * 33, ln, 20, C["mute"], 400)
    return svg(W, H, b, BG_DEFS)

# ---------- social card 1080x1350 ----------
def social():
    W, H = 1080, 1350
    b = backdrop(W, H)
    b += mark(W / 2 - 64, 96, 128)
    b += t(W / 2, 300, "استوديو المقالات", 104, C["text"], 800, "middle")
    b += t(W / 2, 372, "اكتب وصمّم وانشر لمواقعك من هاتفك", 38, C["gold"], 800, "middle")
    b += phone(W / 2 - 187, 430, 1.25, "غرفة التحكم")
    b += rect(70, 1290, 940, 4, 2, fill="url(#gold)")
    x = W - 80
    for lab in ("مزودون مجانيون", "أغلفة واقعية", "نشر مباشر"):
        s, w = pill(x, 1228, lab, 22, fill="#1b2542"); b += s; x -= w + 14
    return svg(W, H, b, BG_DEFS)

if __name__ == "__main__":
    save("hero.svg", hero()); save("control-room.svg", control_room()); save("architecture.svg", architecture())
    save("providers.svg", providers()); save("features.svg", features()); save("social-card.svg", social())
