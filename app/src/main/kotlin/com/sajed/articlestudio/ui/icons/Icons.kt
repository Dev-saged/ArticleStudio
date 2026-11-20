package com.sajed.articlestudio.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** عائلة أيقونات واحدة: شبكة 24dp، سماكة 1.8، رؤوس وزوايا مستديرة. الاتجاهية تنعكس في RTL. */
object Icons {
    private fun icon(name: String, mirror: Boolean = false, vararg d: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror)
        d.forEach {
            b.addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Generate = icon("Generate", false, "M13 2 4 14h7l-1 8 9-12h-7z")
    val Articles = icon("Articles", false, "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
    val Providers = icon("Providers", false, "M12 15a4 4 0 1 1-8 0a4 4 0 0 1 8 0z", "M11 12l9-9M16 7l3 3M14 9l2 2")
    val Export = icon("Export", false, "M12 16V4M6 10l6-6 6 6M5 20h14")
    val Sun = icon("Sun", false, "M16 12a4 4 0 1 1-8 0a4 4 0 0 1 8 0z", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Moon = icon("Moon", false, "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val Play = icon("Play", false, "M7 4.5v15l12-7.5z")
    val Pause = icon("Pause", false, "M8 5v14M16 5v14")
    val Stop = icon("Stop", false, "M6 6h12v12H6z")
    val Check = icon("Check", false, "M5 12.5l4.5 4.5L19 7.5")
    val Warning = icon("Warning", false, "M12 3 2.5 20h19z", "M12 10v4.5M12 17.5h.01")
    val Back = icon("Back", true, "M15 5l-7 7 7 7")
    val Next = icon("Next", true, "M9 5l7 7-7 7")
    val Plus = icon("Plus", false, "M12 5v14M5 12h14")
    val Close = icon("Close", false, "M6 6l12 12M18 6 6 18")
    val Refresh = icon("Refresh", false, "M20 11a8 8 0 1 0-2.3 6M20 4v7h-7")
    val Image = icon("Image", false, "M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15.5 9.5h.01")
    val Document = icon("Document", false, "M7 3h7l5 5v13H7z", "M14 3v5h5", "M10 13h6M10 17h6")
    val Settings = icon("Settings", false, "M4 7h10M18 7h2M4 17h2M10 17h10", "M16 5v4M8 15v4")
    val Copy = icon("Copy", false, "M9 9h11v11H9z", "M5 15V4h11")
    val Link = icon("Link", false, "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Clock = icon("Clock", false, "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 7v5l3 2")
    val Trash = icon("Trash", false, "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13")
    val Edit = icon("Edit", false, "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Search = icon("Search", false, "M16 16l4.5 4.5", "M17 10.5a6.5 6.5 0 1 1-13 0 6.5 6.5 0 0 1 13 0z")
    val Eye = icon("Eye", false, "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
    val EyeOff = icon("EyeOff", false, "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M4 4l16 16")
    val Sites = icon("Sites", false, "M3 7.5 12 3l9 4.5-9 4.5z", "M3 12l9 4.5 9-4.5", "M3 16.5 12 21l9-4.5")
    val Info = icon("Info", false, "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 11v5M12 8h.01")
    val Layers = icon("Layers", false, "M12 4l8 4-8 4-8-4z", "M4 12l8 4 8-4", "M4 16l8 4 8-4")
    val Share = icon("Share", false, "M19.5 5.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 0 1 5 0z", "M9.5 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 0 1 5 0z", "M19.5 18.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 0 1 5 0z", "M9.2 10.8l5.6-3.6M9.2 13.2l5.6 3.6")
    val Grid = icon("Grid", false, "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
    val Bell = icon("Bell", false, "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0")
    val Star = icon("Star", false, "M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z")
    val Chart = icon("Chart", false, "M4 20V10M10 20V4M16 20v-7M21 20H3")
    val Cloud = icon("Cloud", false, "M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1-1 9z")
    val Shield = icon("Shield", false, "M12 3l7 3v6c0 4-3 7.5-7 9-4-1.5-7-5-7-9V6z", "M9 12l2 2 4-4")
    val Key = icon("Key", false, "M11.5 15a3.5 3.5 0 1 1-7 0a3.5 3.5 0 0 1 7 0z", "M10.5 12.5L19 4M16 7l2.5 2.5M14 9l2 2")
    val Filter = icon("Filter", false, "M4 6h16M7 12h10M10 18h4")
    val Minus = icon("Minus", false, "M5 12h14")
    val Skip = icon("Skip", true, "M6 6l7 6-7 6z", "M16 6v12")
    val Wand = icon("Wand", false, "M4 20L15 9", "M14 4v3M19 9h-3M17.5 5.5l-2 2M18 14v2M20 15h-4")
    val Board = icon("Board", false, "M4 5h5v14H4z", "M10 5h5v9h-5z", "M16 5h4v6h-4z")
    val List = icon("List", false, "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    val More = icon("More", false, "M5 12h.01M12 12h.01M19 12h.01")
    val Instagram = icon("Instagram", false, "M4 9a5 5 0 0 1 5-5h6a5 5 0 0 1 5 5v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z", "M15.6 12a3.6 3.6 0 1 1-7.2 0a3.6 3.6 0 0 1 7.2 0z", "M16.8 7.2h.01")
    val XLogo = icon("XLogo", false, "M5 4h3.6L19 20h-3.6z", "M18.6 4l-5.8 6.6M11.2 13.4L5.4 20")
    val LinkedIn = icon("LinkedIn", false, "M4 7a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3z", "M8 10.5V16M8 7.8h.01M11.5 16v-5.5M11.5 13c0-1.6 1-2.5 2.3-2.5S16 11.3 16 13v3")
    val Facebook = icon("Facebook", false, "M14 21v-7h2.5l.5-3h-3V9.2c0-.9.3-1.5 1.6-1.5H17V5.1A20 20 0 0 0 14.8 5C12.6 5 11 6.3 11 8.8V11H8.5v3H11v7")
    val Telegram = icon("Telegram", false, "M20 5L3.5 11.3l5.3 1.9L18 7.5l-7.3 6.6.3 5.1 2.9-3.4 3.6 2.6z")
    val Bluesky = icon("Bluesky", false, "M12 11c-1.5-3-5-6.5-7-6.5-1.5 0-1.5 2 0 6 1 2.5 3.5 3 5 2.7-2.5.5-4 2-2 4s4 .5 4-1.2c0 1.7 2 3.2 4 1.2s.5-3.5-2-4c1.5.3 4-.2 5-2.7 1.5-4 1.5-6 0-6-2 0-5.5 3.5-7 6.5z")
    val Threads = icon("Threads", false, "M16.5 11.5c-.4-3-2.3-4.5-4.7-4.5-2.8 0-4.8 2.3-4.8 5.2 0 3 2.2 5.3 5.2 5.3 2.3 0 4-1.2 4.6-3.2.4-1.5-.2-3.3-2.3-3.6-2.4-.4-4 .6-4 2.1 0 1.2 1 1.9 2.2 1.9 2 0 2.6-2 2.6-4.7")
}
