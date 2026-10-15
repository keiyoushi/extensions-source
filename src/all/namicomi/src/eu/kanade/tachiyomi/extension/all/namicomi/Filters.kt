package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.extension.all.namicomi.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.StatusDto
import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlQueryFilter {
    fun addQueryParameter(url: HttpUrl.Builder, extLang: String)
}

class HasAvailableChaptersFilter :
    Filter.CheckBox("Has available chapters"),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        if (state) {
            url.addQueryParameter("hasAvailableChapters", "true")
            url.addQueryParameter("availableTranslatedLanguages[]", extLang)
        }
    }
}

class ContentRating(name: String, val value: String) : Filter.CheckBox(name)

class ContentRatingList :
    Filter.Group<ContentRating>("Content rating", contentRatings),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        state.filter(ContentRating::state)
            .forEach { url.addQueryParameter("contentRatings[]", it.value) }
    }
}

private val contentRatings get() = listOf(
    ContentRating("Safe", ContentRatingDto.SAFE.value),
    ContentRating("Restricted", ContentRatingDto.RESTRICTED.value),
    ContentRating("Mature", ContentRatingDto.MATURE.value),
)

class Status(name: String, val value: String) : Filter.CheckBox(name)

class StatusList :
    Filter.Group<Status>("Status", status),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        state.filter(Status::state)
            .forEach { url.addQueryParameter("publicationStatuses[]", it.value) }
    }
}

private val status get() = listOf(
    Status("Ongoing", StatusDto.ONGOING.value),
    Status("Completed", StatusDto.COMPLETED.value),
    Status("Hiatus", StatusDto.HIATUS.value),
    Status("Cancelled", StatusDto.CANCELLED.value),
)

class Sortable(val title: String, val value: String) {
    override fun toString(): String = title
}

private val sortables = arrayOf(
    Sortable("Alphabetic", "title"),
    Sortable("Chapter count", "chapterCount"),
    Sortable("Followers", "followCount"),
    Sortable("Likes", "reactions"),
    Sortable("Comment count", "commentCount"),
    Sortable("Content created at", "publishedAt"),
    Sortable("Views", "views"),
    Sortable("Year", "year"),
    Sortable("Rating", "rating"),
)

class SortFilter :
    Filter.Sort(
        "Sort",
        sortables.map(Sortable::title).toTypedArray(),
        Selection(5, false),
    ),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        if (state != null) {
            val query = sortables[state!!.index].value
            val value = if (state!!.ascending) "asc" else "desc"

            url.addQueryParameter("order[$query]", value)
        }
    }
}

class Tag(val id: String, name: String) : Filter.TriState(name)

class TagList(collection: String, tags: List<Tag>) :
    Filter.Group<Tag>(collection, tags),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        state.forEach { tag ->
            if (tag.isIncluded()) {
                url.addQueryParameter("includedTags[]", tag.id)
            } else if (tag.isExcluded()) {
                url.addQueryParameter("excludedTags[]", tag.id)
            }
        }
    }
}

class TagMode(val title: String, val value: String) {
    override fun toString(): String = title
}

private val tagModes = arrayOf(
    TagMode("And", "and"),
    TagMode("Or", "or"),
)

private class TagInclusionMode :
    Filter.Select<TagMode>("Included tags mode", tagModes, 0),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        url.addQueryParameter("includedTagsMode", values[state].value)
    }
}

private class TagExclusionMode :
    Filter.Select<TagMode>("Excluded tags mode", tagModes, 1),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        url.addQueryParameter("excludedTagsMode", values[state].value)
    }
}

class TagsFilterMode :
    Filter.Group<Filter<*>>("Tags mode", tagsFilterMode),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
        state.filterIsInstance<UrlQueryFilter>()
            .forEach { filter -> filter.addQueryParameter(url, extLang) }
    }
}

private val tagsFilterMode get() = listOf<Filter<*>>(
    TagInclusionMode(),
    TagExclusionMode(),
)
