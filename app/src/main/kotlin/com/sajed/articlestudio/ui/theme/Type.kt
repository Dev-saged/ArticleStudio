package com.sajed.articlestudio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sajed.articlestudio.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun alex(w: FontWeight) = Font(R.font.alexandria, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

val DisplayFont = FontFamily(alex(FontWeight.Medium), alex(FontWeight.Bold), alex(FontWeight.ExtraBold))
val BodyFont = FontFamily(
    Font(R.font.almarai_regular, FontWeight.Normal),
    Font(R.font.almarai_bold, FontWeight.Bold),
    Font(R.font.almarai_extrabold, FontWeight.ExtraBold),
)

private const val TNUM = "tnum"

val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    labelLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, fontFeatureSettings = TNUM),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFeatureSettings = TNUM),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, fontFeatureSettings = TNUM),
)

val NumberStyle = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, fontFeatureSettings = TNUM)

/** عزل LTR للأرقام داخل النص العربي. */
fun String.ltr() = "⁦$this⁩"
