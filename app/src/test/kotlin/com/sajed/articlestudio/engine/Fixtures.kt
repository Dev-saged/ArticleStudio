package com.sajed.articlestudio.engine

fun sampleArticle(slug: String = "messi-world-cup-record", title: String = "كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم", motif: String = "ball") = Article(
    title = title, desc = "تحليل مبسط لأبرز أرقام النجم الأرجنتيني وما تعنيه للمنتخب في البطولات الكبرى مع قراءة هادئة للمستقبل.",
    slug = slug, labels = listOf("أخبار", "كأس العالم"), kicker = "تحليل", motif = motif, hue = 20, infotitle = "أهم الأرقام",
    facts = listOf(Fact("26", "مباراة في البطولة"), Fact("13", "هدفاً"), Fact("8", "تمريرات حاسمة"), Fact("2022", "سنة اللقب")),
    tldr = "رقم قياسي جديد يعكس استمرارية نادرة على أعلى مستوى.",
    intro = "<p>" + "كلمة ".repeat(60) + "</p>",
    sections = List(4) { Section("القسم ${it + 1}", "<p>" + "نص ".repeat(120) + "</p>") },
    faq = listOf(Faq("كم مباراة لعب؟", "لعب ست وعشرين مباراة."), Faq("متى كان آخر لقب؟", "في عام ألفين واثنين وعشرين.")),
    outro = "<p>خلاصة قصيرة.</p>", ref = Ref("عنوان خبر من ESPN", "https://www.espn.com/soccer/story/_/id/1/x", "تفاصيل", 1_700_000_000_000),
    words = 700, provider = "groq", model = "llama", topic = "ميسي",
)
