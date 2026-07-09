package eu.kanade.tachiyomi.extension.ar.mangacloud

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.jsonInstance
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

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
        val json = jsonInstance.parseToJsonElement(response.body.string()).jsonObject
        val documents = json["documents"]?.jsonArray ?: return MangaListResult(emptyList(), null)
        val mangas = documents.mapNotNull { doc ->
            val fields = doc.jsonObject["fields"]?.jsonObject ?: return@mapNotNull null
            val mangaId = doc.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.substringAfterLast("/") ?: return@mapNotNull null
            val altTitles = fields.stringArray("alternativeTitles")
            val genres = fields.stringArray("genres")
            val statusStr = fields.string("status")
            MangaData(
                smanga = SManga.create().apply {
                    url = "/manga/$mangaId"
                    title = fields.string("title")
                    thumbnail_url = fields.string("coverImage")
                    description = fields.string("description")
                    author = fields.string("author")
                    artist = fields.string("artist")
                    genre = genres.joinToString(", ")
                    status = when (statusStr) {
                        "مستمر" -> SManga.ONGOING
                        "مكتمل" -> SManga.COMPLETED
                        "متروك", "متروكة" -> SManga.ON_HIATUS
                        "معلق" -> SManga.ON_HIATUS
                        else -> SManga.UNKNOWN
                    }
                },
                altTitles = altTitles,
                genres = genres,
                statusString = when (statusStr) {
                    "مستمر" -> "ongoing"
                    "مكتمل" -> "completed"
                    else -> ""
                },
            )
        }
        val nextPageToken = json["nextPageToken"]?.jsonPrimitive?.contentOrNull
        return MangaListResult(mangas, nextPageToken)
    }

    fun parseDetails(response: Response): SManga {
        val json = jsonInstance.parseToJsonElement(response.body.string()).jsonObject
        val fields = json["fields"]?.jsonObject ?: return SManga.create()
        val mangaId = json["name"]?.jsonPrimitive?.contentOrNull?.substringAfterLast("/") ?: ""
        return SManga.create().apply {
            url = "/manga/$mangaId"
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
        val json = jsonInstance.parseToJsonElement(response.body.string()).jsonObject
        val documents = json["documents"]?.jsonArray ?: return emptyList()
        return documents.mapNotNull { doc ->
            val fields = doc.jsonObject["fields"]?.jsonObject ?: return@mapNotNull null
            val docName = doc.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val chapterNum = docName.substringAfterLast("/")
            val num = chapterNum.toIntOrNull() ?: 0
            SChapter.create().apply {
                url = docName.substringAfterLast("cloudmangas/")
                name = "الفصل $num"
                chapter_number = num.toFloat()
            }
        }.sortedByDescending { it.chapter_number }
    }

    fun parsePages(response: Response): List<Page> {
        val json = jsonInstance.parseToJsonElement(response.body.string()).jsonObject
        val fields = json["fields"]?.jsonObject ?: return emptyList()
        val pagesArray = fields["pages"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray ?: return emptyList()
        return pagesArray.mapIndexed { index, value ->
            Page(index, imageUrl = value.jsonObject["stringValue"]?.jsonPrimitive?.contentOrNull ?: "")
        }
    }
}

private fun JsonObject.string(key: String): String = this[key]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""

private fun JsonObject.stringArray(key: String): List<String> = this[key]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
    ?.map { it.jsonObject["stringValue"]?.jsonPrimitive?.contentOrNull ?: "" }
    ?: emptyList()
