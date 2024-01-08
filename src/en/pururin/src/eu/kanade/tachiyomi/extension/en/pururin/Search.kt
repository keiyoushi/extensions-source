package eu.kanade.tachiyomi.extension.en.pururin

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object Search {
    private val jsonMime = "application/json".toMediaType()

    enum class Sort(private val label: String, val id: String) {
        NEWEST("Newest", "newest"),
        POPULAR("Most Popular", "most-popular"),
        RATING("Highest Rated", "highest-rated"),
        VIEWS("Most Viewed", "most-viewed"),
        TITLE("Title", "title"),
        ;

        override fun toString() = label
    }

    enum class TagMode(val id: String) {
        AND("1"), OR("2");

        override fun toString() = name
    }

    operator fun invoke(
        sort: Sort,
        page: Int = 1,
        query: String = "",
        whitelist: List<Int> = emptyList(),
        blacklist: List<Int> = emptyList(),
        mode: TagMode = TagMode.AND,
        range: IntRange = 0..100,
    ) = buildJsonObject {
        putJsonObject("search") {
            put("sort", sort.id)
            put("PageNumber", page)
            putJsonObject("manga") {
                put("string", query)
                put("sort", "1")
            }
            putJsonObject("tag") {
                putJsonObject("items") {
                    putJsonArray("whitelisted") {
                        whitelist.forEach {
                            addJsonObject { put("id", it) }
                        }
                    }
                    putJsonArray("blacklisted") {
                        blacklist.forEach {
                            addJsonObject { put("id", it) }
                        }
                    }
                }
                put("sort", mode.id)
            }
            putJsonObject("page") {
                putJsonArray("range") {
                    add(range.first)
                    add(range.last)
                }
            }
        }
    }.toString().toRequestBody(jsonMime)

    fun info(id: String) = buildJsonObject {
        put("id", id)
        put("type", "1")
    }.toString().toRequestBody(jsonMime)
}
