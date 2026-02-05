package eu.kanade.tachiyomi.extension.en.flamecomics

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
class NewBuildID(
    val buildId: String,
)

@Serializable
class MangaDetailsResponseData(
    val pageProps: PageProps,
) {
    @Serializable
    class PageProps(
        val series: Series,
    )
}

@Serializable
class ChapterListResponseData(
    val pageProps: PageProps,
) {
    @Serializable
    class PageProps(
        val chapters: List<Chapter>,
    )

    @Serializable
    data class Chapter(
        val chapter: Double,
        val title: String?,
        val release_date: Long,
        val series_id: Int,
        val token: String,
    )
}

@Serializable
class SearchPageData(
    val pageProps: PageProps,
) {
    @Serializable
    class PageProps(
        val series: List<Series>,
    )
}

@Serializable
class LatestPageData(
    val pageProps: PageProps,
) {
    @Serializable
    class PageProps(
        val latestEntries: LatestEntries,
    ) {
        @Serializable
        class LatestEntries(
            val blocks: List<Block>,
        ) {
            @Serializable
            class Block(
                val series: List<Series>,
            )
        }
    }
}

@Serializable
class ChapterPageData(
    val pageProps: PageProps,
) {
    @Serializable
    class PageProps(
        val chapter: ChapterPage,
    )

    @Serializable
    data class ChapterPage(
        val release_date: Long,
        val series_id: Int,
        val token: String,
        @Serializable(with = KeysToListSerializer::class)
        val images: List<Page>,
    )
}

@Serializable
class Series(
    val title: String,
    val altTitles: List<String>?,
    val description: String?,
    val cover: String,
    val type: String,
    val tags: List<String>?,
    val author: List<String>?,
    val artist: List<String>?,
    val status: String,
    val series_id: Int?,
    val last_edit: Long,
    val views: Int?,
)

@Serializable
class Page(
    val name: String,
)

class KeysToListSerializer : KSerializer<List<Page>> {
    private val listSer = MapSerializer(String.serializer(), Page.serializer())
    override val descriptor: SerialDescriptor = listSer.descriptor
    override fun deserialize(decoder: Decoder): List<Page> = listSer.deserialize(decoder).flatMap { k -> listOf(k.value) }

    override fun serialize(encoder: Encoder, value: List<Page>) {}
}
