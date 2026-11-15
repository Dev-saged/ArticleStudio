package com.sajed.articlestudio.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** بصمة SHA-1 لشهادة توقيع التطبيق الحالية، وهي ما يطلبه Google Cloud لعميل OAuth من نوع Android. */
object SigningInfo {
    @Suppress("DEPRECATION")
    fun sha1(ctx: Context): String = runCatching {
        val pm = ctx.packageManager
        val cert = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        } else pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        cert?.let { format(MessageDigest.getInstance("SHA-1").digest(it.toByteArray())) }.orEmpty()
    }.getOrDefault("")

    fun format(b: ByteArray) = b.joinToString(":") { "%02X".format(it) }
}
