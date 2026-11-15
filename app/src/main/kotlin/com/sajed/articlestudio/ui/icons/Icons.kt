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
    val Play = icon("Play", true, "M7 4.5v15l12-7.5z")
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
}
