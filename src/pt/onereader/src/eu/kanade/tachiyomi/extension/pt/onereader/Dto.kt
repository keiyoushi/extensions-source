package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
internal class SearchDto(
    private val data: List<MangaDto>,
    private val page: Int,
    private val totalPages: Int,
) {
    fun toMangasPage() = MangasPage(
        data.map(MangaDto::toSManga),
        page < totalPages,
    )
}

@Serializable
internal class HomeMangaDto(
    @SerialName("manga_key")
    private val mangaKey: String,
    @SerialName("display_name")
    private val displayName: String,
    private val status: String,
    @SerialName("cover_image")
    private val coverImage: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = mangaKey
        title = displayName
        thumbnail_url = coverImage
        status = this@HomeMangaDto.status.toStatus()
    }
}

@Serializable
internal class MangaDto(
    @SerialName("manga_key")
    private val mangaKey: String,
    @SerialName("display_name")
    private val displayName: String,
    private val name: String,
    @SerialName("japanese_name")
    private val japaneseName: String?,
    private val author: String? = null,
    private val artist: String? = null,
    private val synopsis: String?,
    private val status: String,
    private val type: String,
    @SerialName("origin_country")
    private val originCountry: String,
    private val year: Int,
    @Serializable(with = TagsSerializer::class)
    private val tags: List<String>,
    @SerialName("cover_image")
    private val coverImage: String,
    @SerialName("publisher_name")
    private val publisherName: String? = null,
) {
    fun toSManga(details: Boolean = false): SManga = SManga.create().apply {
        url = mangaKey
        title = displayName
        thumbnail_url = coverImage
        author = this@MangaDto.author?.takeIf(String::isNotBlank)
        artist = this@MangaDto.artist?.takeIf(String::isNotBlank)
        genre = tags.takeIf(List<String>::isNotEmpty)?.joinToString()
        status = this@MangaDto.status.toStatus()
        description = buildDescription()
        initialized = details
    }

    private fun buildDescription(): String? {
        val metadata = buildList {
            name.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
                ?.let { add("Título original: $it") }
            japaneseName?.takeIf {
                it.isNotBlank() &&
                    !it.equals(displayName, ignoreCase = true) &&
                    !it.equals(name, ignoreCase = true)
            }?.let { add("Título nativo: $it") }
            type.takeIf(String::isNotBlank)?.let { add("Tipo: $it") }
            originCountry.takeIf(String::isNotBlank)?.let { add("País: $it") }
            add("Ano: $year")
            publisherName?.takeIf(String::isNotBlank)?.let { add("Editora: $it") }
        }

        return buildString {
            synopsis?.takeIf(String::isNotBlank)?.let(::append)
            if (isNotEmpty() && metadata.isNotEmpty()) append("\n\n")
            append(metadata.joinToString("\n"))
        }.ifBlank { null }
    }
}

@Serializable
internal class ChapterDto(
    @SerialName("manga_key")
    private val mangaKey: String,
    @SerialName("posted_date")
    private val postedDate: String,
) {
    fun toSChapter(number: String): SChapter = SChapter.create().apply {
        url = "$mangaKey/$number"
        memo = buildJsonObject {
            put("id", mangaKey)
            put("number", number)
        }
        name = "Capítulo $number"
        chapter_number = number.toFloatOrNull() ?: -1F
        date_upload = chapterDateFormat.tryParse(postedDate)
    }
}

@Serializable
internal class PagesDto(
    private val pages: List<String>,
) {
    fun toPages(apiBaseUrl: HttpUrl): List<Page> = pages.mapIndexed { index, path ->
        Page(
            index = index,
            imageUrl = requireNotNull(apiBaseUrl.resolve(path)).toString(),
        )
    }
}

private object TagsSerializer : JsonTransformingSerializer<List<String>>(
    ListSerializer(String.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        JsonNull -> JsonArray(emptyList())
        is JsonPrimitive ->
            element.contentOrNull
                ?.let(jsonInstance::parseToJsonElement)
                ?: element
        else -> element
    }
}

private fun String.toStatus(): Int = when (trim().lowercase(Locale.ROOT)) {
    "em lançamento", "lançando" -> SManga.ONGOING
    "completo" -> SManga.COMPLETED
    "hiatus", "hiato" -> SManga.ON_HIATUS
    "cancelado" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
