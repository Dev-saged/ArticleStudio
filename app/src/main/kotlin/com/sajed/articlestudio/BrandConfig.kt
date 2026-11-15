package com.sajed.articlestudio

import com.sajed.articlestudio.ui.theme.Oklch

/** المصدر الوحيد للهوية: الاسم والألوان والوحدات والترخيص. عميل جديد = تعديل هذا الملف فقط. */
object BrandConfig {
    const val NAME = "استوديو المقالات"
    const val DYNAMIC_COLOR = false
    const val BLOG_NAME = "سبورت لايف 24"
    const val BLOG_SITE = "https://sport1live24.blogspot.com"
    const val BLOG_AUTHOR = "ساجد العبادلة"
    const val DEFAULT_IMAGES_REPO = "Dev-saged/bloger"
    const val UPDATE_REPO = "Dev-saged/ArticleStudio"
    val LICENSE_EXPIRY: String? = null
    val MODULES = listOf("generate", "articles", "providers", "export")

    class Palette(
        val bg: Oklch, val bg2: Oklch, val surface: Oklch, val line: Oklch,
        val text: Oklch, val muted: Oklch, val red: Oklch, val gold: Oklch,
        val goldText: Oklch, val board: Oklch, val glow: Oklch, val ok: Oklch,
    )

    val dark = Palette(
        bg = Oklch(0.14f, 0.02f, 262f), bg2 = Oklch(0.17f, 0.025f, 262f),
        surface = Oklch(0.205f, 0.032f, 262f), line = Oklch(0.33f, 0.04f, 262f),
        text = Oklch(0.96f, 0.01f, 262f), muted = Oklch(0.74f, 0.03f, 262f),
        red = Oklch(0.68f, 0.22f, 27f), gold = Oklch(0.85f, 0.15f, 88f),
        goldText = Oklch(0.85f, 0.15f, 88f), board = Oklch(0.17f, 0.03f, 262f),
        glow = Oklch(0.26f, 0.09f, 27f, 0.5f), ok = Oklch(0.8f, 0.16f, 155f),
    )
    val light = Palette(
        bg = Oklch(0.975f, 0.008f, 262f), bg2 = Oklch(0.96f, 0.012f, 262f),
        surface = Oklch(1f, 0f, 262f), line = Oklch(0.87f, 0.02f, 262f),
        text = Oklch(0.2f, 0.035f, 262f), muted = Oklch(0.48f, 0.03f, 262f),
        red = Oklch(0.57f, 0.22f, 27f), gold = Oklch(0.85f, 0.15f, 88f),
        goldText = Oklch(0.5f, 0.13f, 70f), board = Oklch(0.955f, 0.02f, 262f),
        glow = Oklch(0.9f, 0.07f, 27f, 0.55f), ok = Oklch(0.5f, 0.14f, 155f),
    )
}
