package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.multisrc.loneseal.ChapterPagesResponseDto
import eu.kanade.tachiyomi.multisrc.loneseal.LoneSeal
import eu.kanade.tachiyomi.multisrc.loneseal.UrlLayout
import keiyoushi.annotation.Source
import keiyoushi.utils.getLocalStorage
import okhttp3.Headers

@Source
abstract class Mangakuri : LoneSeal() {
    override val urlLayout = UrlLayout.LEGACY_COMIC
    override val overloadedGenres = super.overloadedGenres + setOf("yaoi")

    private var bearerToken: String? = null

    override suspend fun Headers.Builder.configureChapterHeaders() = apply {
        bearerToken = bearerToken ?: getLocalStorage(baseUrl, "token")
        bearerToken?.let { set("Authorization", "Bearer $it") }
    }

    override fun onEmptyPages(dto: ChapterPagesResponseDto) {
        if (dto.chapter.passwordRequired) error("Password required")

        if (dto.chapter.loginRequired == true) {
            bearerToken = null
            error("Login in WebView and retry")
        }
    }
}
