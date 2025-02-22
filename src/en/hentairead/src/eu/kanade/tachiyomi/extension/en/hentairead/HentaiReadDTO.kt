package eu.kanade.tachiyomi.extension.en.hentairead
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class Results(
    val results: List<Result>,
)

@Serializable
class Result(
    val id: Int,
    val text: String,
)

@Serializable
class ImageBaseUrlDto(
    val baseUrl: String,
)

@Serializable
class PagesDto(
    val data: Data,
) {
    @Serializable
    class Data(
        val chapter: Chapter,
    ) {
        @Serializable
        class Chapter(
            val images: List<Image>,
        ) {
            @Serializable
            class Image(
                val src: String,
            )
        }
    }
}
