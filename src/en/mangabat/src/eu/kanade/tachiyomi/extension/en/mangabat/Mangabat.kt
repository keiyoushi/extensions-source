package eu.kanade.tachiyomi.extension.en.mangabat

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Mangabat : MangaBox(
    "Mangabat",
    arrayOf(
        "www.mangabats.com",
    ),
    "en",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Mangabat\" to \"Mangabat\" to continue reading"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.contains("mangabat.com/")) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaSlug = manga.url
            .substringAfter("/manga/")
            .substringBefore("?")
            .substringBefore("#")
            .trim()

        if (mangaSlug.isEmpty()) {
            return super.chapterListRequest(manga)
        }

        val apiUrl = "$baseUrl/api/manga/$mangaSlug/chapters?limit=1500&offset=0"
        return GET(apiUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val isApiResponse = response.request.url.toString().contains("/api/manga/")

        if (!isApiResponse) {
            return super.chapterListParse(response)
        }

        val mangaSlug = response.request.url.toString()
            .substringAfter("/api/manga/")
            .substringBefore("/chapters")

        try {
            val limit = 1500
            val firstApiUrl = "$baseUrl/api/manga/$mangaSlug/chapters?limit=$limit&offset=0"
            val firstRequest = GET(firstApiUrl, headers)
            val firstResponse = client.newCall(firstRequest).execute()

            if (!firstResponse.isSuccessful) {
                firstResponse.close()
                val mangaUrl = "$baseUrl/manga/$mangaSlug"
                val htmlResponse = client.newCall(GET(mangaUrl, headers)).execute()
                return super.chapterListParse(htmlResponse)
            }

            val firstJsonString = firstResponse.body.string()
            firstResponse.close()

            val firstJsonElement = json.parseToJsonElement(firstJsonString)

            val (firstChaptersArray, firstPaginationInfo) = when {
                firstJsonElement is JsonObject && firstJsonElement.containsKey("data") -> {
                    val dataObj = firstJsonElement["data"]!!.jsonObject
                    val pagination = if (dataObj.containsKey("pagination")) {
                        dataObj["pagination"]!!.jsonObject
                    } else {
                        null
                    }
                    if (dataObj.containsKey("chapters")) {
                        Pair(dataObj["chapters"]!!.jsonArray, pagination)
                    } else if (dataObj.containsKey("data") && dataObj["data"] is JsonArray) {
                        Pair(dataObj["data"]!!.jsonArray, pagination)
                    } else {
                        throw Exception("Could not find chapters array in data object")
                    }
                }
                firstJsonElement is JsonObject && firstJsonElement.containsKey("chapters") -> {
                    Pair(firstJsonElement["chapters"]!!.jsonArray, null as JsonObject?)
                }
                firstJsonElement is JsonObject && firstJsonElement.containsKey("results") -> {
                    Pair(firstJsonElement["results"]!!.jsonArray, null as JsonObject?)
                }
                firstJsonElement is JsonArray -> {
                    Pair(firstJsonElement, null as JsonObject?)
                }
                else -> {
                    throw Exception("Could not find chapters array in API response")
                }
            }

            val totalChapters = mutableListOf<SChapter>()
            totalChapters.addAll(parseChaptersArray(firstChaptersArray, mangaSlug))

            val hasMore = when {
                firstPaginationInfo != null && firstPaginationInfo.containsKey("has_more") -> {
                    firstPaginationInfo["has_more"]!!.jsonPrimitive.content.toBoolean()
                }
                firstChaptersArray.size < limit -> {
                    false
                }
                else -> {
                    true
                }
            }

            if (hasMore) {
                runBlocking {
                    var currentOffset = limit
                    var stillHasMore = true
                    val batchSize = 5

                    while (stillHasMore) {
                        val batchOffsets = mutableListOf<Int>()
                        for (i in 0 until batchSize) {
                            batchOffsets.add(currentOffset + (i * limit))
                        }

                        val results = coroutineScope {
                            batchOffsets.map { offset ->
                                async {
                                    try {
                                        val apiUrl = "$baseUrl/api/manga/$mangaSlug/chapters?limit=$limit&offset=$offset"
                                        val apiRequest = GET(apiUrl, headers)
                                        val apiResponse = client.newCall(apiRequest).execute()

                                        if (!apiResponse.isSuccessful) {
                                            apiResponse.close()
                                            return@async Pair(emptyList<SChapter>(), false)
                                        }

                                        val jsonString = apiResponse.body.string()
                                        apiResponse.close()

                                        val jsonElement = json.parseToJsonElement(jsonString)

                                        val (chaptersArray, paginationInfo) = when {
                                            jsonElement is JsonObject && jsonElement.containsKey("data") -> {
                                                val dataObj = jsonElement["data"]!!.jsonObject
                                                val pagination = if (dataObj.containsKey("pagination")) {
                                                    dataObj["pagination"]!!.jsonObject
                                                } else {
                                                    null
                                                }
                                                val array = if (dataObj.containsKey("chapters")) {
                                                    dataObj["chapters"]!!.jsonArray
                                                } else if (dataObj.containsKey("data") && dataObj["data"] is JsonArray) {
                                                    dataObj["data"]!!.jsonArray
                                                } else {
                                                    return@async Pair(emptyList<SChapter>(), false)
                                                }
                                                Pair(array, pagination)
                                            }
                                            jsonElement is JsonObject && jsonElement.containsKey("chapters") -> {
                                                Pair(jsonElement["chapters"]!!.jsonArray, null as JsonObject?)
                                            }
                                            jsonElement is JsonObject && jsonElement.containsKey("results") -> {
                                                Pair(jsonElement["results"]!!.jsonArray, null as JsonObject?)
                                            }
                                            jsonElement is JsonArray -> {
                                                Pair(jsonElement, null as JsonObject?)
                                            }
                                            else -> {
                                                return@async Pair(emptyList<SChapter>(), false)
                                            }
                                        }

                                        val hasMoreInThisBatch = when {
                                            paginationInfo != null && paginationInfo.containsKey("has_more") -> {
                                                paginationInfo["has_more"]!!.jsonPrimitive.content.toBoolean()
                                            }
                                            chaptersArray.size < limit -> {
                                                false
                                            }
                                            else -> {
                                                true
                                            }
                                        }

                                        Pair(parseChaptersArray(chaptersArray, mangaSlug), hasMoreInThisBatch)
                                    } catch (e: Exception) {
                                        Pair(emptyList<SChapter>(), false)
                                    }
                                }
                            }.awaitAll()
                        }

                        var foundData = false
                        var foundHasMore = false

                        for ((batchChapters, hasMoreInBatch) in results) {
                            if (batchChapters.isNotEmpty()) {
                                totalChapters.addAll(batchChapters)
                                foundData = true
                            }
                            if (hasMoreInBatch) {
                                foundHasMore = true
                            }
                        }

                        if (!foundData && !foundHasMore) {
                            stillHasMore = false
                        } else {
                            currentOffset += (batchSize * limit)
                            if (!foundHasMore) {
                                stillHasMore = false
                            }
                        }
                    }
                }
            }

            val allChapters = totalChapters

            return if (allChapters.isNotEmpty()) {
                val firstChapter = allChapters.first()
                val lastChapter = allChapters.last()

                val firstChapterNum = extractChapterNumber(firstChapter.name)
                val lastChapterNum = extractChapterNumber(lastChapter.name)

                val needsReverse = when {
                    firstChapterNum != null && lastChapterNum != null -> {
                        firstChapterNum < lastChapterNum
                    }
                    firstChapter.date_upload > 0 && lastChapter.date_upload > 0 -> {
                        firstChapter.date_upload < lastChapter.date_upload
                    }
                    else -> {
                        true
                    }
                }

                if (needsReverse) {
                    allChapters.reversed()
                } else {
                    allChapters
                }
            } else {
                val mangaUrl = "$baseUrl/manga/$mangaSlug"
                val htmlResponse = client.newCall(GET(mangaUrl, headers)).execute()
                super.chapterListParse(htmlResponse)
            }
        } catch (e: Exception) {
            val mangaUrl = "$baseUrl/manga/$mangaSlug"
            try {
                val htmlResponse = client.newCall(GET(mangaUrl, headers)).execute()
                return super.chapterListParse(htmlResponse)
            } catch (fallbackException: Exception) {
                throw Exception("API parsing failed: ${e.message}. HTML fallback also failed: ${fallbackException.message}")
            }
        }
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L

        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("MMM-dd-yyyy HH:mm", Locale.ENGLISH),
        )

        for (format in formats) {
            try {
                return format.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    private fun extractChapterNumber(chapterName: String): Double? {
        if (chapterName.isBlank()) return null

        val patterns = listOf(
            Regex("""(?i)chapter\s+(\d+\.?\d*)"""),
            Regex("""(?i)ch\.?\s*(\d+\.?\d*)"""),
            Regex("""^(\d+\.?\d*)"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(chapterName)
            if (match != null) {
                val numberStr = match.groupValues[1]
                return numberStr.toDoubleOrNull()
            }
        }

        return null
    }

    private fun parseChaptersArray(chaptersArray: JsonArray, mangaSlug: String): List<SChapter> {
        return chaptersArray.mapNotNull { chapterElement ->
            try {
                val chapterObj = chapterElement.jsonObject

                SChapter.create().apply {
                    name = when {
                        chapterObj.containsKey("name") -> chapterObj["name"]!!.jsonPrimitive.content
                        chapterObj.containsKey("title") -> chapterObj["title"]!!.jsonPrimitive.content
                        chapterObj.containsKey("chapter_name") -> chapterObj["chapter_name"]!!.jsonPrimitive.content
                        chapterObj.containsKey("chapter") -> "Chapter ${chapterObj["chapter"]!!.jsonPrimitive.content}"
                        else -> "Chapter ${chapterObj["id"]?.jsonPrimitive?.content ?: "Unknown"}"
                    }

                    url = when {
                        chapterObj.containsKey("chapter_slug") -> {
                            val slug = chapterObj["chapter_slug"]!!.jsonPrimitive.content
                            if (slug.startsWith("/")) {
                                slug
                            } else {
                                "/manga/$mangaSlug/$slug"
                            }
                        }
                        chapterObj.containsKey("url") -> {
                            val urlStr = chapterObj["url"]!!.jsonPrimitive.content
                            if (urlStr.startsWith("http")) {
                                urlStr
                            } else if (urlStr.startsWith("/")) {
                                urlStr
                            } else {
                                "/manga/$mangaSlug/$urlStr"
                            }
                        }
                        chapterObj.containsKey("slug") -> {
                            val slug = chapterObj["slug"]!!.jsonPrimitive.content
                            "/manga/$mangaSlug/$slug"
                        }
                        chapterObj.containsKey("chapter_num") -> {
                            val num = chapterObj["chapter_num"]!!.jsonPrimitive.content
                            "/manga/$mangaSlug/chapter-$num"
                        }
                        chapterObj.containsKey("id") -> {
                            val id = chapterObj["id"]!!.jsonPrimitive.content
                            "/manga/$mangaSlug/chapter-$id"
                        }
                        else -> {
                            val chapterNum = chapterObj["chapter"]?.jsonPrimitive?.content ?: chapterObj["chapter_num"]?.jsonPrimitive?.content
                                ?: chapterObj["id"]?.jsonPrimitive?.content ?: "unknown"
                            "/manga/$mangaSlug/chapter-$chapterNum"
                        }
                    }

                    date_upload = when {
                        chapterObj.containsKey("created_at") -> {
                            parseDate(chapterObj["created_at"]!!.jsonPrimitive.content)
                        }
                        chapterObj.containsKey("updated_at") -> {
                            parseDate(chapterObj["updated_at"]!!.jsonPrimitive.content)
                        }
                        chapterObj.containsKey("date") -> {
                            parseDate(chapterObj["date"]!!.jsonPrimitive.content)
                        }
                        chapterObj.containsKey("createdAt") -> {
                            parseDate(chapterObj["createdAt"]!!.jsonPrimitive.content)
                        }
                        else -> 0L
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
