package eu.kanade.tachiyomi.extension.ar.mangacloud

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

@Serializable
data class FirestoreListResponse(
    val documents: List<FirestoreDocument>? = null,
    val nextPageToken: String? = null,
)

@Serializable
data class FirestoreDocument(
    val name: String? = null,
    val fields: JsonObject? = null,
)

data class MangaData(
    val smanga: SManga,
    val altTitles: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val statusString: String = "",
)

data class MangaListResult(
    val mangas: List<MangaData>,
    val nextPageToken: String?,
)

object FirestoreParser {

    fun parseList(response: Response): MangaListResult {
        val result = response.parseAs<FirestoreListResponse>()
        val mangas = result.documents?.mapNotNull { doc ->
            val fields = doc.fields ?: return@mapNotNull null
            val mangaId = doc.name?.substringAfterLast("/") ?: return@mapNotNull null
            MangaData(
                smanga = SManga.create().apply {
                    url = mangaId
                    title = fields.string("title")
                    thumbnail_url = fields.string("coverImage")
                    description = fields.string("description")
                    author = fields.string("author")
                    artist = fields.string("artist")
                    genre = fields.stringArray("genres").joinToString(", ")
                    status = when (fields.string("status")) {
                        "مستمر" -> SManga.ONGOING
                        "مكتمل" -> SManga.COMPLETED
                        "متروك", "متروكة" -> SManga.ON_HIATUS
                        "معلق" -> SManga.ON_HIATUS
                        else -> SManga.UNKNOWN
                    }
                },
                altTitles = fields.stringArray("alternativeTitles"),
                genres = fields.stringArray("genres"),
                statusString = when (fields.string("status")) {
                    "مستمر" -> "ongoing"
                    "مكتمل" -> "completed"
                    else -> ""
                },
            )
        } ?: emptyList()
        return MangaListResult(mangas, result.nextPageToken)
    }

    fun parseDetails(response: Response): SManga {
        val doc = response.parseAs<FirestoreDocument>()
        val fields = doc.fields!!
        val mangaId = doc.name!!.substringAfterLast("/")
        check(mangaId.isNotEmpty()) { "mangaId must not be empty" }
        return SManga.create().apply {
            url = mangaId
            title = fields.string("title")
            thumbnail_url = fields.string("coverImage")
            description = fields.string("description")
            author = fields.string("author")
            artist = fields.string("artist")
            genre = fields.stringArray("genres").joinToString(", ")
            status = when (fields.string("status")) {
                "مستمر" -> SManga.ONGOING
                "مكتمل" -> SManga.COMPLETED
                "متروك" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    fun parseChapters(response: Response): List<SChapter> {
        val result = response.parseAs<FirestoreListResponse>()
        return result.documents?.mapNotNull { doc ->
            val docName = doc.name ?: return@mapNotNull null
            val chapterNum = docName.substringAfterLast("/")
            val num = chapterNum.toIntOrNull() ?: 0
            SChapter.create().apply {
                url = docName.substringAfterLast("cloudmangas/")
                name = "الفصل $num"
                chapter_number = num.toFloat()
            }
        }?.sortedByDescending { it.chapter_number } ?: emptyList()
    }

    fun parsePages(response: Response): List<Page> {
        val doc = response.parseAs<FirestoreDocument>()
        val fields = doc.fields ?: return emptyList()
        val pagesArray = fields["pages"]?.jsonObject?.get("arrayValue")?.jsonObject
            ?.get("values")?.jsonArray ?: return emptyList()
        return pagesArray.mapIndexed { index, value ->
            Page(index, imageUrl = value.jsonObject["stringValue"]?.jsonPrimitive?.contentOrNull ?: "")
        }
    }
}

private fun JsonObject.string(key: String): String = this[key]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""

private fun JsonObject.stringArray(key: String): List<String> = this[key]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
    ?.map { it.jsonObject["stringValue"]?.jsonPrimitive?.contentOrNull ?: "" }
    ?: emptyList()
