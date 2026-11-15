package com.sajed.articlestudio.engine

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** تفويض Blogger عبر Google Identity: صامت بعد الموافقة الأولى، وإلا يعيد نافذة موافقة لتعرضها الواجهة. */
@Singleton
class GoogleAuth @Inject constructor(@ApplicationContext private val ctx: Context) {
    sealed interface Result {
        class Token(val token: String, val email: String?) : Result
        class Consent(val sender: IntentSender) : Result
        class Failure(val message: String) : Result
    }

    suspend fun authorize(account: String? = null): Result = suspendCancellableCoroutine { c ->
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).apply {
            if (!account.isNullOrBlank()) setAccount(Account(account, "com.google"))
        }.build()
        Identity.getAuthorizationClient(ctx).authorize(req)
            .addOnSuccessListener { c.resume(map(it)) }
            .addOnFailureListener { c.resume(Result.Failure(fail(it))) }
    }

    /** نتيجة نافذة الموافقة بعد عودة المستخدم. */
    fun fromIntent(data: Intent?): Result = try { map(Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)) } catch (e: Exception) { Result.Failure(fail(e)) }

    private fun map(r: AuthorizationResult): Result {
        r.pendingIntent?.takeIf { r.hasResolution() }?.let { return Result.Consent(it.intentSender) }
        val t = r.accessToken ?: return Result.Failure("لم يصل رمز وصول من جوجل")
        return Result.Token(t, runCatching { r.toGoogleSignInAccount()?.email }.getOrNull())
    }

    private fun fail(e: Throwable): String = when {
        e.message?.contains("10") == true || e.message?.contains("DEVELOPER_ERROR", true) == true ->
            "بصمة التوقيع أو اسم الحزمة غير مطابقين لعميل OAuth في Google Cloud (الخطوة 4 في الدليل)"
        e.message?.contains("16") == true || e.message?.contains("CANCEL", true) == true -> "أُلغي الربط"
        else -> clamp(e.message, 140).ifBlank { "تعذّر الاتصال بخدمات جوجل" }
    }

    companion object { const val SCOPE = "https://www.googleapis.com/auth/blogger" }
}
