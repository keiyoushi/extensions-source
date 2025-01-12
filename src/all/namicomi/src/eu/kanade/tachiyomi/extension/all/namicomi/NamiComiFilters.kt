package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.extension.all.namicomi.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.StatusDto
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

class NamiComiFilters {

    internal fun getFilterList(intl: Intl): FilterList = FilterList(
        HasAvailableChaptersFilter(intl),
        ContentRatingList(intl, getContentRatings(intl)),
        StatusList(intl, getStatus(intl)),
        SortFilter(intl, getSortables(intl)),
        TagsFilter(intl, getTagFilters(intl)),
        TagList(intl["content"], getContents(intl)),
        TagList(intl["format"], getFormats(intl)),
        TagList(intl["genre"], getGenres(intl)),
        TagList(intl["theme"], getThemes(intl)),
    )

    private interface UrlQueryFilter {
        fun addQueryParameter(url: HttpUrl.Builder, extLang: String)
    }

    private class HasAvailableChaptersFilter(intl: Intl) :
        Filter.CheckBox(intl["has_available_chapters"]),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
            if (state) {
                url.addQueryParameter("hasAvailableChapters", "true")
                url.addQueryParameter("availableTranslatedLanguages[]", extLang)
            }
        }
    }

    private class ContentRating(name: String, val value: String) : Filter.CheckBox(name)
    private class ContentRatingList(intl: Intl, contentRating: List<ContentRating>) :
        Filter.Group<ContentRating>(intl["content_rating"], contentRating),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
            state.filter(ContentRating::state)
                .forEach { url.addQueryParameter("contentRatings[]", it.value) }
        }
    }

    private fun getContentRatings(intl: Intl) = listOf(
        ContentRating(intl["content_rating_safe"], ContentRatingDto.SAFE.value),
        ContentRating(intl["content_rating_restricted"], ContentRatingDto.RESTRICTED.value),
        ContentRating(intl["content_rating_mature"], ContentRatingDto.MATURE.value),
    )

    private class Status(name: String, val value: String) : Filter.CheckBox(name)
    private class StatusList(intl: Intl, status: List<Status>) :
        Filter.Group<Status>(intl["status"], status),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
            state.filter(Status::state)
                .forEach { url.addQueryParameter("publicationStatuses[]", it.value) }
        }
    }

    private fun getStatus(intl: Intl) = listOf(
        Status(intl["status_ongoing"], StatusDto.ONGOING.value),
        Status(intl["status_completed"], StatusDto.COMPLETED.value),
        Status(intl["status_hiatus"], StatusDto.HIATUS.value),
        Status(intl["status_cancelled"], StatusDto.CANCELLED.value),
    )

    data class Sortable(val title: String, val value: String) {
        override fun toString(): String = title
    }

    private fun getSortables(intl: Intl) = arrayOf(
        Sortable(intl["sort_alphabetic"], "title"),
        Sortable(intl["sort_number_of_chapters"], "chapterCount"),
        Sortable(intl["sort_number_of_follows"], "followCount"),
        Sortable(intl["sort_number_of_likes"], "reactions"),
        Sortable(intl["sort_number_of_comments"], "commentCount"),
        Sortable(intl["sort_content_created_at"], "publishedAt"),
        Sortable(intl["sort_views"], "views"),
        Sortable(intl["sort_year"], "year"),
        Sortable(intl["sort_rating"], "rating"),
    )

    class SortFilter(intl: Intl, private val sortables: Array<Sortable>) :
        Filter.Sort(
            intl["sort"],
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

    internal class Tag(val id: String, name: String) : Filter.TriState(name)

    private class TagList(collection: String, tags: List<Tag>) :
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

    private fun getContents(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("drugs", intl["content_warnings_drugs"]),
            Tag("gambling", intl["content_warnings_gambling"]),
            Tag("gore", intl["content_warnings_gore"]),
            Tag("mental-disorders", intl["content_warnings_mental_disorders"]),
            Tag("physical-abuse", intl["content_warnings_physical_abuse"]),
            Tag("racism", intl["content_warnings_racism"]),
            Tag("self-harm", intl["content_warnings_self_harm"]),
            Tag("sexual-abuse", intl["content_warnings_sexual_abuse"]),
            Tag("verbal-abuse", intl["content_warnings_verbal_abuse"]),
        )

        return tags.sortIfTranslated(intl)
    }

    private fun getFormats(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("4-koma", intl["format_4_koma"]),
            Tag("adaptation", intl["format_adaptation"]),
            Tag("anthology", intl["format_anthology"]),
            Tag("full-color", intl["format_full_color"]),
            Tag("oneshot", intl["format_oneshot"]),
            Tag("silent", intl["format_silent"]),
        )

        return tags.sortIfTranslated(intl)
    }

    private fun getGenres(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("action", intl["genre_action"]),
            Tag("adventure", intl["genre_adventure"]),
            Tag("boys-love", intl["genre_boys_love"]),
            Tag("comedy", intl["genre_comedy"]),
            Tag("crime", intl["genre_crime"]),
            Tag("drama", intl["genre_drama"]),
            Tag("fantasy", intl["genre_fantasy"]),
            Tag("girls-love", intl["genre_girls_love"]),
            Tag("historical", intl["genre_historical"]),
            Tag("horror", intl["genre_horror"]),
            Tag("isekai", intl["genre_isekai"]),
            Tag("mecha", intl["genre_mecha"]),
            Tag("medical", intl["genre_medical"]),
            Tag("mystery", intl["genre_mystery"]),
            Tag("philosophical", intl["genre_philosophical"]),
            Tag("psychological", intl["genre_psychological"]),
            Tag("romance", intl["genre_romance"]),
            Tag("sci-fi", intl["genre_sci_fi"]),
            Tag("slice-of-life", intl["genre_slice_of_life"]),
            Tag("sports", intl["genre_sports"]),
            Tag("superhero", intl["genre_superhero"]),
            Tag("thriller", intl["genre_thriller"]),
            Tag("tragedy", intl["genre_tragedy"]),
            Tag("wuxia", intl["genre_wuxia"]),
        )

        return tags.sortIfTranslated(intl)
    }

    private fun getThemes(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("aliens", intl["theme_aliens"]),
            Tag("animals", intl["theme_animals"]),
            Tag("cooking", intl["theme_cooking"]),
            Tag("crossdressing", intl["theme_crossdressing"]),
            Tag("delinquents", intl["theme_delinquents"]),
            Tag("demons", intl["theme_demons"]),
            Tag("genderswap", intl["theme_genderswap"]),
            Tag("ghosts", intl["theme_ghosts"]),
            Tag("gyaru", intl["theme_gyaru"]),
            Tag("harem", intl["theme_harem"]),
            Tag("mafia", intl["theme_mafia"]),
            Tag("magic", intl["theme_magic"]),
            Tag("magical-girls", intl["theme_magical_girls"]),
            Tag("martial-arts", intl["theme_martial_arts"]),
            Tag("military", intl["theme_military"]),
            Tag("monster-girls", intl["theme_monster_girls"]),
            Tag("monsters", intl["theme_monsters"]),
            Tag("music", intl["theme_music"]),
            Tag("ninja", intl["theme_ninja"]),
            Tag("office-workers", intl["theme_office_workers"]),
            Tag("police", intl["theme_police"]),
            Tag("post-apocalyptic", intl["theme_post_apocalyptic"]),
            Tag("reincarnation", intl["theme_reincarnation"]),
            Tag("reverse-harem", intl["theme_reverse_harem"]),
            Tag("samurai", intl["theme_samurai"]),
            Tag("school-life", intl["theme_school_life"]),
            Tag("supernatural", intl["theme_supernatural"]),
            Tag("survival", intl["theme_survival"]),
            Tag("time-travel", intl["theme_time_travel"]),
            Tag("traditional-games", intl["theme_traditional_games"]),
            Tag("vampires", intl["theme_vampires"]),
            Tag("video-games", intl["theme_video_games"]),
            Tag("villainess", intl["theme_villainess"]),
            Tag("virtual-reality", intl["theme_virtual_reality"]),
            Tag("zombies", intl["theme_zombies"]),
        )

        return tags.sortIfTranslated(intl)
    }

    // Tags taken from: https://api.namicomi.com/title/tags
    internal fun getTags(intl: Intl): List<Tag> {
        return getContents(intl) + getFormats(intl) + getGenres(intl) + getThemes(intl)
    }

    private data class TagMode(val title: String, val value: String) {
        override fun toString(): String = title
    }

    private fun getTagModes(intl: Intl) = arrayOf(
        TagMode(intl["mode_and"], "and"),
        TagMode(intl["mode_or"], "or"),
    )

    private class TagInclusionMode(intl: Intl, modes: Array<TagMode>) :
        Filter.Select<TagMode>(intl["included_tags_mode"], modes, 0),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
            url.addQueryParameter("includedTagsMode", values[state].value)
        }
    }

    private class TagExclusionMode(intl: Intl, modes: Array<TagMode>) :
        Filter.Select<TagMode>(intl["excluded_tags_mode"], modes, 1),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
            url.addQueryParameter("excludedTagsMode", values[state].value)
        }
    }

    private class TagsFilter(intl: Intl, innerFilters: FilterList) :
        Filter.Group<Filter<*>>(intl["tags_mode"], innerFilters),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, extLang: String) {
            state.filterIsInstance<UrlQueryFilter>()
                .forEach { filter -> filter.addQueryParameter(url, extLang) }
        }
    }

    private fun getTagFilters(intl: Intl): FilterList = FilterList(
        TagInclusionMode(intl, getTagModes(intl)),
        TagExclusionMode(intl, getTagModes(intl)),
    )

    internal fun addFiltersToUrl(url: HttpUrl.Builder, filters: FilterList, extLang: String): HttpUrl {
        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { filter -> filter.addQueryParameter(url, extLang) }

        return url.build()
    }

    private fun List<Tag>.sortIfTranslated(intl: Intl): List<Tag> = apply {
        if (intl.chosenLanguage == NamiComiConstants.english) {
            return this
        }

        return sortedWith(compareBy(intl.collator, Tag::name))
    }
}
