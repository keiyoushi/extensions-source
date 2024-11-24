package eu.kanade.tachiyomi.extension.en.flamecomics

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy

@Serializable
class MangaPageData(
    val props: Props,
)

@Serializable
class Props(
    val pageProps: PageProps,
)

@Serializable
class PageProps(
    val chapters: List<Chapter>,
    val series: Series,
)

@Serializable
class SearchPageData(
    val props: SProps,
)

@Serializable
class SProps(
    val pageProps: SPageProps,
)

@Serializable
class SPageProps(
    val series: List<Series>,
)

@Serializable
class Series(
    val title: String,
    val altTitles: String,
    val description: String,
    val cover: String,
    val author: String,
    val status: String,
    val series_id: Int,
    val last_edit: String,
    val views: Int?,

)

@Serializable
class Chapter(
    val chapter: Double,
    val title: String?,
    val release_date: Long,
    val token: String,
    @Serializable(with = keystoListSerializer::class)
    val images: List<Page>,
)

@Serializable
class Page(
    val name: String,
)

class keystoListSerializer : KSerializer<List<Page>> {
    private val listSer = MapSerializer(String.serializer(), Page.serializer())
    override val descriptor: SerialDescriptor = listSer.descriptor
    override fun deserialize(decoder: Decoder): List<Page> {
        return listSer.deserialize(decoder).flatMap { k -> listOf(k.value) }
    }

    override fun serialize(encoder: Encoder, value: List<Page>) {}
}

val json: Json by injectLazy()

inline fun <reified T> getJsonData(document: Document?): T? {
    val jsonData = document?.getElementById("__NEXT_DATA__")?.data() ?: return null
    return json.decodeFromString<T>(jsonData)
}
