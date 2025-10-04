package eu.kanade.tachiyomi.extension.en.bookwalker

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class BookWalkerFilters(private val bookwalker: BookWalker) {
    var categories: List<FilterInfo>? = null
        private set
    var genres: List<FilterInfo>? = null
        private set

    // Author filter disabled for now, since the performance/UX in-app is pretty bad
//    var authors: List<FilterInfo>? = null
//        private set
    var publishers: List<FilterInfo>? = null
        private set

    private val fetchMutex = Mutex()
    private var hasObtainedFilters = false

    fun fetchIfNecessaryInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fetchIfNecessary()
            } catch (e: Exception) {
                Log.e("bookwalker", e.toString())
            }
        }
    }

    // Lock applied so that we don't try to make additional new requests before the first set of
    // requests have finished.
    private suspend fun fetchIfNecessary() = fetchMutex.withLock {
        // In theory the list of filters could change while the app is alive, but in practice that
        // seems fairly unlikely, and we can save a lot of unnecessary calls by assuming it won't.
        if (hasObtainedFilters) {
            return
        }

        coroutineScope {
            listOf(
                async { if (categories == null) categories = fetchFilters("categories") },
                async { if (genres == null) genres = fetchFilters("genre") },
//                async { if (authors == null) authors = fetchFilters("authors") },
                async { if (publishers == null) publishers = fetchFilters("publishers") },
            ).awaitAll()

            hasObtainedFilters = true
        }
    }

    private suspend fun fetchFilters(entityName: String): List<FilterInfo> {
        val entityPath = "/$entityName/"
        val response = bookwalker.client.newCall(
            GET(bookwalker.baseUrl + entityPath, bookwalker.callHeaders),
        ).await()
        val document = response.asJsoup()
        return document.select(".link-list > li > a").map {
            FilterInfo(
                it.text(),
                it.attr("href").removePrefix(entityPath).trimEnd('/'),
            )
        }
    }
}

class FilterInfo(name: String, val id: String) : Filter.CheckBox(name) {
    override fun toString(): String {
        return name
    }
}

interface QueryParamFilter {
    fun getQueryParams(): List<Pair<String, String>>
}

class SelectOneFilter(
    name: String,
    private val queryParam: String,
    options: List<FilterInfo>,
) : QueryParamFilter, Filter.Select<FilterInfo>(
    name,
    options.toTypedArray(),
    max(0, options.indexOfFirst { it.id == "2" }), // Default to manga
) {
    override fun getQueryParams(): List<Pair<String, String>> {
        return listOf(queryParam to values[state].id)
    }
}

class SelectMultipleFilter(
    name: String,
    private val queryParam: String,
    options: List<FilterInfo>,
) : QueryParamFilter, Filter.Group<FilterInfo>(name, options) {
    override fun getQueryParams(): List<Pair<String, String>> {
        return listOf(
            queryParam to state.filter { it.state }.joinToString(",") { it.id },
        )
    }
}

class OthersFilter : QueryParamFilter, Filter.Group<FilterInfo>(
    "Others",
    listOf(
        FilterInfo("On Sale", "qspp"),
        FilterInfo("Coin Boost", "qcon"),
        FilterInfo("Pre-Order", "qcos"),
        FilterInfo("Completed", "qcpl"),
        FilterInfo("Bonus Item", "qspe"),
        FilterInfo("Exclude Purchased", "qseq"),
    ),
) {
    override fun getQueryParams(): List<Pair<String, String>> {
        return state.filter { it.state }.map { it.id to "1" }
    }
}

class ExcludeFilter : QueryParamFilter, Filter.Text("Exclude search terms (comma-separated)") {
    override fun getQueryParams(): List<Pair<String, String>> {
        return state.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { "qnot[]" to it.replace(" ", "+") }
    }
}

class TextFilter(
    name: String,
    private val queryParam: String,
    defaultValue: String = "",
) : QueryParamFilter, Filter.Text(name, defaultValue) {
    override fun getQueryParams(): List<Pair<String, String>> {
        return listOf(queryParam to state)
    }
}

class PriceFilter : QueryParamFilter, Filter.Group<TextFilter>(
    "Price",
    listOf(
        TextFilter("Min Price ($)", "qpri_min"),
        TextFilter("Max Price ($)", "qpri_max"),
    ),
) {
    override fun getQueryParams(): List<Pair<String, String>> {
        return state.filter { it.state.isNotEmpty() }.flatMap { it.getQueryParams() }
    }
}
