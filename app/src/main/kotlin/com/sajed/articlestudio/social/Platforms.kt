package com.sajed.articlestudio.social

/** kind: api = ناشر برمجي بتوكن دائم؛ web = جلسة متصفح معزولة وأتمتة واجهة الموقع. */
class PlatformSpec(
    val id: String, val name: String, val kind: String, val max: Int, val urlLen: Int? = null, val tags: Int = 0,
    val loginUrl: String = "", val needs: List<String> = emptyList(), val cookieUrl: String = "", val cookies: List<String> = emptyList(),
) {
    val isWeb get() = kind == "web"
}

object Platforms {
    val all = listOf(
        PlatformSpec("facebook", "فيسبوك", "web", 2000, tags = 0, loginUrl = "https://m.facebook.com/login/", cookieUrl = "https://m.facebook.com", cookies = listOf("c_user", "xs")),
        PlatformSpec("x", "X (تويتر)", "web", 280, urlLen = 23, tags = 2, loginUrl = "https://x.com/i/flow/login", cookieUrl = "https://x.com", cookies = listOf("auth_token")),
        PlatformSpec("threads", "ثريدز", "web", 500, tags = 2, loginUrl = "https://www.threads.com/login", cookieUrl = "https://www.threads.com", cookies = listOf("sessionid")),
        PlatformSpec("linkedin", "لينكدإن", "web", 3000, tags = 2, loginUrl = "https://www.linkedin.com/login", cookieUrl = "https://www.linkedin.com", cookies = listOf("li_at")),
        PlatformSpec("telegram", "تيليغرام", "api", 4096, needs = listOf("token", "chat")),
        PlatformSpec("bluesky", "بلوسكي", "api", 300, tags = 2, needs = listOf("handle", "password")),
    )
    fun of(id: String) = all.firstOrNull { it.id == id }
}
