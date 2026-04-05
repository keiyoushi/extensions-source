package eu.kanade.tachiyomi.extension.en.bookwalker

import android.util.Log
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.FilterDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.FilterInfoDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.LimitOffsetDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchFilterOptionsDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchFilterOptionsRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchFilterOptionsResponseDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchHeaderRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchHeaderResponseDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchPageType
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchPageTypeDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SeriesFormat
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SortDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.TagFilterDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.TagInclusionMode
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BookWalkerFilters(private val bookwalker: BookWalker) {
    var genreFilters: List<TaggedTriState<String>>? = null
        private set

    // There are an enormous amount of tags and while it's not hard to fetch the complete list,
    // Tachiyomi clients typically do not handle large lists of tags well at the moment.
    // BookWalker handles it by allowing users to search for tags by name, but that capability
    // is not supported by the Tachiyomi API.
    // For now, all of the secondary filters will be disabled, but some with a smaller number of
    // items like status (currently broken on BW's side) and launch year can be supported later.
//    var secondaryFilters: List<TriStateFilter>? = null
//        private set

    private val fetchMutex = Mutex()
    private var hasObtainedFilters = false

    fun fetchIfNecessaryInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fetchIfNecessary()
            } catch (e: Throwable) {
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
            return@withLock
        }

        val response = bookwalker.client.newCall(
            POST(
                bookwalker.endpoint("ContentService/SearchHeader"),
                bookwalker.headers,
                SearchHeaderRequestDto().toProtoRequestBody(),
            ),
        ).await().parseProtoAs<SearchHeaderResponseDto>()

        genreFilters = getAllFilters(response.genres).map { TaggedTriState(it.name, it.id) }

        hasObtainedFilters = true
    }

    private suspend fun getAllFilters(initialList: SearchFilterOptionsDto): List<FilterInfoDto> {
        if (!initialList.hasMore) {
            return initialList.options
        } else {
            val results = mutableListOf<FilterInfoDto>()

            var lastResponse: SearchFilterOptionsResponseDto? = null
            do {
                lastResponse = bookwalker.client.newCall(
                    POST(
                        bookwalker.endpoint("CollectionService/SearchFilterOptionsV2"),
                        bookwalker.headers,
                        SearchFilterOptionsRequestDto(
                            filterType = initialList.filterType,
                            limitOffset = LimitOffsetDto(100, lastResponse?.countInfo?.offset ?: 0),
                            searchDomain = SearchPageTypeDto(SearchPageType.Browse()),
                        ).toProtoRequestBody(),
                    ),
                ).await().parseProtoAs<SearchFilterOptionsResponseDto>()
                results.addAll(lastResponse.results)
            } while (lastResponse.countInfo.limit + lastResponse.countInfo.offset <= lastResponse.countInfo.totalCount)

            return results
        }
    }
}

interface SearchFilter {
    fun process(request: SearchRequestDto): SearchRequestDto
}

object SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf(
            "Relevance",
            "Popular",
            "Updated Latest",
            "Alphabetical",
            "Newest",
        ),
        Selection(0, true),
    ),
    SearchFilter {
    override fun process(request: SearchRequestDto): SearchRequestDto = request.copy(
        sort = when (state?.index) {
            0 -> SortDto.RELEVANCE
            1 -> SortDto.POPULAR
            2 -> SortDto.LAST_UPDATED
            3 -> SortDto.ALPHABETICAL_ASC
            4 -> SortDto.NEWEST
            else -> SortDto.RELEVANCE
        }.let {
            if (state?.ascending == false) it.reverse() else it
        },
    )
}

class TaggedCheckbox<T>(name: String, val id: T, state: Boolean = false) : Filter.CheckBox(name, state)
class TaggedTriState<T>(name: String, val id: T, state: Int = STATE_IGNORE) : Filter.TriState(name, state)

object FormatFilter :
    Filter.Group<TaggedCheckbox<SeriesFormat>>(
        "Format",
        listOf(
            TaggedCheckbox("Manga", SeriesFormat.MANGA),
            TaggedCheckbox("Webtoons", SeriesFormat.WEBTOON),
        ),
    ),
    SearchFilter {
    override fun process(request: SearchRequestDto): SearchRequestDto = request.copy(
        formats = state.filter { it.state }.map { it.id }
            .takeIf { it.isNotEmpty() } ?: listOf(SeriesFormat.MANGA, SeriesFormat.WEBTOON),
    )
}

class TriStateFilter(
    name: String,
    val filterType: String,
    options: List<TaggedTriState<String>>,
) : Filter.Group<TaggedTriState<String>>(name, options),
    SearchFilter {
    override fun process(request: SearchRequestDto): SearchRequestDto = request.copy(
        filters = request.filters + FilterDto(
            filterType,
            state.mapNotNull {
                TagFilterDto(
                    it.id,
                    when (it.state) {
                        TriState.STATE_INCLUDE -> TagInclusionMode.INCLUDE
                        TriState.STATE_EXCLUDE -> TagInclusionMode.EXCLUDE
                        else -> return@mapNotNull null
                    },
                )
            },
        ),
    )
}
