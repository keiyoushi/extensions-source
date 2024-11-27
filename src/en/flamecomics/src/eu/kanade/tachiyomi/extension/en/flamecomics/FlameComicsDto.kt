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
class MangaPageData(
    val pageProps: PageProps,
) {
    @Serializable
    class PageProps(
        val chapters: List<Chapter>,
        val series: Series,
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
        val chapter: Chapter,
    )
}

@Serializable
class Series(
    val title: String,
    val altTitles: String?,
    val description: String,
    val cover: String,
    val author: String?,
    val status: String,
    val series_id: Int,
    val views: Int?,
)

@Serializable
class Chapter(
    val chapter: Double,
    val title: String?,
    val release_date: Long,
    val series_id: Int,
    val token: String,
    @Serializable(with = KeysToListSerializer::class)
    val images: List<Page>,
)

@Serializable
class Page(
    val name: String,
)

class KeysToListSerializer : KSerializer<List<Page>> {
    private val listSer = MapSerializer(String.serializer(), Page.serializer())
    override val descriptor: SerialDescriptor = listSer.descriptor
    override fun deserialize(decoder: Decoder): List<Page> {
        return listSer.deserialize(decoder).flatMap { k -> listOf(k.value) }
    }

    override fun serialize(encoder: Encoder, value: List<Page>) {}
}
