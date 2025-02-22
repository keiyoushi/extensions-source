package eu.kanade.tachiyomi.extension.all.mangadex

import android.content.SharedPreferences
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.PublicationDemographicDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.StatusDto
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

class MangaDexFilters {

    internal fun getMDFilterList(
        preferences: SharedPreferences,
        dexLang: String,
        intl: Intl,
    ): FilterList = FilterList(
        HasAvailableChaptersFilter(intl),
        OriginalLanguageList(intl, getOriginalLanguage(preferences, dexLang, intl)),
        ContentRatingList(intl, getContentRating(preferences, dexLang, intl)),
        DemographicList(intl, getDemographics(intl)),
        StatusList(intl, getStatus(intl)),
        SortFilter(intl, getSortables(intl)),
        TagsFilter(intl, getTagFilters(intl)),
        TagList(intl["content"], getContents(intl)),
        TagList(intl["format"], getFormats(intl)),
        TagList(intl["genre"], getGenres(intl)),
        TagList(intl["theme"], getThemes(intl)),
    )

    private interface UrlQueryFilter {
        fun addQueryParameter(url: HttpUrl.Builder, dexLang: String)
    }

    private class HasAvailableChaptersFilter(intl: Intl) :
        Filter.CheckBox(intl["has_available_chapters"]),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            if (state) {
                url.addQueryParameter("hasAvailableChapters", "true")
                url.addQueryParameter("availableTranslatedLanguage[]", dexLang)
            }
        }
    }

    private class OriginalLanguage(
        name: String,
        val isoCode: String,
        state: Boolean = false,
    ) : Filter.CheckBox(name, state)
    private class OriginalLanguageList(intl: Intl, originalLanguage: List<OriginalLanguage>) :
        Filter.Group<OriginalLanguage>(intl["original_language"], originalLanguage),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            state.filter(OriginalLanguage::state)
                .forEach { lang ->
                    // dex has zh and zh-hk for chinese manhua
                    if (lang.isoCode == MDConstants.originalLanguagePrefValChinese) {
                        url.addQueryParameter(
                            "originalLanguage[]",
                            MDConstants.originalLanguagePrefValChineseHk,
                        )
                    }

                    url.addQueryParameter("originalLanguage[]", lang.isoCode)
                }
        }
    }

    private fun getOriginalLanguage(
        preferences: SharedPreferences,
        dexLang: String,
        intl: Intl,
    ): List<OriginalLanguage> {
        val originalLanguages = preferences.getStringSet(
            MDConstants.getOriginalLanguagePrefKey(dexLang),
            setOf(),
        )!!

        return listOf(
            OriginalLanguage(
                name = intl.format(
                    "original_language_filter_japanese",
                    intl.languageDisplayName(MangaDexIntl.JAPANESE),
                ),
                isoCode = MDConstants.originalLanguagePrefValJapanese,
                state = MDConstants.originalLanguagePrefValJapanese in originalLanguages,
            ),
            OriginalLanguage(
                name = intl.format(
                    "original_language_filter_chinese",
                    intl.languageDisplayName(MangaDexIntl.CHINESE),
                ),
                isoCode = MDConstants.originalLanguagePrefValChinese,
                state = MDConstants.originalLanguagePrefValChinese in originalLanguages,
            ),
            OriginalLanguage(
                name = intl.format(
                    "original_language_filter_korean",
                    intl.languageDisplayName(MangaDexIntl.KOREAN),
                ),
                isoCode = MDConstants.originalLanguagePrefValKorean,
                state = MDConstants.originalLanguagePrefValKorean in originalLanguages,
            ),
        )
    }

    private class ContentRating(name: String, val value: String) : Filter.CheckBox(name)
    private class ContentRatingList(intl: Intl, contentRating: List<ContentRating>) :
        Filter.Group<ContentRating>(intl["content_rating"], contentRating),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            state.filter(ContentRating::state)
                .forEach { url.addQueryParameter("contentRating[]", it.value) }
        }
    }

    private fun getContentRating(
        preferences: SharedPreferences,
        dexLang: String,
        intl: Intl,
    ): List<ContentRating> {
        val contentRatings = preferences.getStringSet(
            MDConstants.getContentRatingPrefKey(dexLang),
            MDConstants.contentRatingPrefDefaults,
        )

        return listOf(
            ContentRating(intl["content_rating_safe"], ContentRatingDto.SAFE.value).apply {
                state = contentRatings?.contains(MDConstants.contentRatingPrefValSafe) ?: true
            },
            ContentRating(intl["content_rating_suggestive"], ContentRatingDto.SUGGESTIVE.value).apply {
                state = contentRatings?.contains(MDConstants.contentRatingPrefValSuggestive) ?: true
            },
            ContentRating(intl["content_rating_erotica"], ContentRatingDto.EROTICA.value).apply {
                state = contentRatings?.contains(MDConstants.contentRatingPrefValErotica) ?: false
            },
            ContentRating(intl["content_rating_pornographic"], ContentRatingDto.PORNOGRAPHIC.value).apply {
                state = contentRatings?.contains(MDConstants.contentRatingPrefValPornographic) ?: false
            },
        )
    }

    private class Demographic(name: String, val value: String) : Filter.CheckBox(name)
    private class DemographicList(intl: Intl, demographics: List<Demographic>) :
        Filter.Group<Demographic>(intl["publication_demographic"], demographics),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            state.filter(Demographic::state)
                .forEach { url.addQueryParameter("publicationDemographic[]", it.value) }
        }
    }

    private fun getDemographics(intl: Intl) = listOf(
        Demographic(intl["publication_demographic_none"], PublicationDemographicDto.NONE.value),
        Demographic(intl["publication_demographic_shounen"], PublicationDemographicDto.SHOUNEN.value),
        Demographic(intl["publication_demographic_shoujo"], PublicationDemographicDto.SHOUJO.value),
        Demographic(intl["publication_demographic_seinen"], PublicationDemographicDto.SEINEN.value),
        Demographic(intl["publication_demographic_josei"], PublicationDemographicDto.JOSEI.value),
    )

    private class Status(name: String, val value: String) : Filter.CheckBox(name)
    private class StatusList(intl: Intl, status: List<Status>) :
        Filter.Group<Status>(intl["status"], status),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            state.filter(Status::state)
                .forEach { url.addQueryParameter("status[]", it.value) }
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
        Sortable(intl["sort_chapter_uploaded_at"], "latestUploadedChapter"),
        Sortable(intl["sort_number_of_follows"], "followedCount"),
        Sortable(intl["sort_content_created_at"], "createdAt"),
        Sortable(intl["sort_content_info_updated_at"], "updatedAt"),
        Sortable(intl["sort_relevance"], "relevance"),
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

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
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

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
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
            Tag("b29d6a3d-1569-4e7a-8caf-7557bc92cd5d", intl["content_gore"]),
            Tag("97893a4c-12af-4dac-b6be-0dffb353568e", intl["content_sexual_violence"]),
        )

        return tags.sortIfTranslated(intl)
    }

    private fun getFormats(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("b11fda93-8f1d-4bef-b2ed-8803d3733170", intl["format_yonkoma"]),
            Tag("f4122d1c-3b44-44d0-9936-ff7502c39ad3", intl["format_adaptation"]),
            Tag("51d83883-4103-437c-b4b1-731cb73d786c", intl["format_anthology"]),
            Tag("0a39b5a1-b235-4886-a747-1d05d216532d", intl["format_award_winning"]),
            Tag("b13b2a48-c720-44a9-9c77-39c9979373fb", intl["format_doujinshi"]),
            Tag("7b2ce280-79ef-4c09-9b58-12b7c23a9b78", intl["format_fan_colored"]),
            Tag("f5ba408b-0e7a-484d-8d49-4e9125ac96de", intl["format_full_color"]),
            Tag("3e2b8dae-350e-4ab8-a8ce-016e844b9f0d", intl["format_long_strip"]),
            Tag("320831a8-4026-470b-94f6-8353740e6f04", intl["format_official_colored"]),
            Tag("0234a31e-a729-4e28-9d6a-3f87c4966b9e", intl["format_oneshot"]),
            Tag("891cf039-b895-47f0-9229-bef4c96eccd4", intl["format_user_created"]),
            Tag("e197df38-d0e7-43b5-9b09-2842d0c326dd", intl["format_web_comic"]),
        )

        return tags.sortIfTranslated(intl)
    }

    private fun getGenres(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("391b0423-d847-456f-aff0-8b0cfc03066b", intl["genre_action"]),
            Tag("87cc87cd-a395-47af-b27a-93258283bbc6", intl["genre_adventure"]),
            Tag("5920b825-4181-4a17-beeb-9918b0ff7a30", intl["genre_boys_love"]),
            Tag("4d32cc48-9f00-4cca-9b5a-a839f0764984", intl["genre_comedy"]),
            Tag("5ca48985-9a9d-4bd8-be29-80dc0303db72", intl["genre_crime"]),
            Tag("b9af3a63-f058-46de-a9a0-e0c13906197a", intl["genre_drama"]),
            Tag("cdc58593-87dd-415e-bbc0-2ec27bf404cc", intl["genre_fantasy"]),
            Tag("a3c67850-4684-404e-9b7f-c69850ee5da6", intl["genre_girls_love"]),
            Tag("33771934-028e-4cb3-8744-691e866a923e", intl["genre_historical"]),
            Tag("cdad7e68-1419-41dd-bdce-27753074a640", intl["genre_horror"]),
            Tag("ace04997-f6bd-436e-b261-779182193d3d", intl["genre_isekai"]),
            Tag("81c836c9-914a-4eca-981a-560dad663e73", intl["genre_magical_girls"]),
            Tag("50880a9d-5440-4732-9afb-8f457127e836", intl["genre_mecha"]),
            Tag("c8cbe35b-1b2b-4a3f-9c37-db84c4514856", intl["genre_medical"]),
            Tag("ee968100-4191-4968-93d3-f82d72be7e46", intl["genre_mystery"]),
            Tag("b1e97889-25b4-4258-b28b-cd7f4d28ea9b", intl["genre_philosophical"]),
            Tag("423e2eae-a7a2-4a8b-ac03-a8351462d71d", intl["genre_romance"]),
            Tag("256c8bd9-4904-4360-bf4f-508a76d67183", intl["genre_sci_fi"]),
            Tag("e5301a23-ebd9-49dd-a0cb-2add944c7fe9", intl["genre_slice_of_life"]),
            Tag("69964a64-2f90-4d33-beeb-f3ed2875eb4c", intl["genre_sports"]),
            Tag("7064a261-a137-4d3a-8848-2d385de3a99c", intl["genre_superhero"]),
            Tag("07251805-a27e-4d59-b488-f0bfbec15168", intl["genre_thriller"]),
            Tag("f8f62932-27da-4fe4-8ee1-6779a8c5edba", intl["genre_tragedy"]),
            Tag("acc803a4-c95a-4c22-86fc-eb6b582d82a2", intl["genre_wuxia"]),
        )

        return tags.sortIfTranslated(intl)
    }

    private fun getThemes(intl: Intl): List<Tag> {
        val tags = listOf(
            Tag("e64f6742-c834-471d-8d72-dd51fc02b835", intl["theme_aliens"]),
            Tag("3de8c75d-8ee3-48ff-98ee-e20a65c86451", intl["theme_animals"]),
            Tag("ea2bc92d-1c26-4930-9b7c-d5c0dc1b6869", intl["theme_cooking"]),
            Tag("9ab53f92-3eed-4e9b-903a-917c86035ee3", intl["theme_crossdressing"]),
            Tag("da2d50ca-3018-4cc0-ac7a-6b7d472a29ea", intl["theme_delinquents"]),
            Tag("39730448-9a5f-48a2-85b0-a70db87b1233", intl["theme_demons"]),
            Tag("2bd2e8d0-f146-434a-9b51-fc9ff2c5fe6a", intl["theme_gender_swap"]),
            Tag("3bb26d85-09d5-4d2e-880c-c34b974339e9", intl["theme_ghosts"]),
            Tag("fad12b5e-68ba-460e-b933-9ae8318f5b65", intl["theme_gyaru"]),
            Tag("aafb99c1-7f60-43fa-b75f-fc9502ce29c7", intl["theme_harem"]),
            Tag("5bd0e105-4481-44ca-b6e7-7544da56b1a3", intl["theme_incest"]),
            Tag("2d1f5d56-a1e5-4d0d-a961-2193588b08ec", intl["theme_loli"]),
            Tag("85daba54-a71c-4554-8a28-9901a8b0afad", intl["theme_mafia"]),
            Tag("a1f53773-c69a-4ce5-8cab-fffcd90b1565", intl["theme_magic"]),
            Tag("799c202e-7daa-44eb-9cf7-8a3c0441531e", intl["theme_martial_arts"]),
            Tag("ac72833b-c4e9-4878-b9db-6c8a4a99444a", intl["theme_military"]),
            Tag("dd1f77c5-dea9-4e2b-97ae-224af09caf99", intl["theme_monster_girls"]),
            Tag("36fd93ea-e8b8-445e-b836-358f02b3d33d", intl["theme_monsters"]),
            Tag("f42fbf9e-188a-447b-9fdc-f19dc1e4d685", intl["theme_music"]),
            Tag("489dd859-9b61-4c37-af75-5b18e88daafc", intl["theme_ninja"]),
            Tag("92d6d951-ca5e-429c-ac78-451071cbf064", intl["theme_office_workers"]),
            Tag("df33b754-73a3-4c54-80e6-1a74a8058539", intl["theme_police"]),
            Tag("9467335a-1b83-4497-9231-765337a00b96", intl["theme_post_apocalyptic"]),
            Tag("3b60b75c-a2d7-4860-ab56-05f391bb889c", intl["theme_psychological"]),
            Tag("0bc90acb-ccc1-44ca-a34a-b9f3a73259d0", intl["theme_reincarnation"]),
            Tag("65761a2a-415e-47f3-bef2-a9dababba7a6", intl["theme_reverse_harem"]),
            Tag("81183756-1453-4c81-aa9e-f6e1b63be016", intl["theme_samurai"]),
            Tag("caaa44eb-cd40-4177-b930-79d3ef2afe87", intl["theme_school_life"]),
            Tag("ddefd648-5140-4e5f-ba18-4eca4071d19b", intl["theme_shota"]),
            Tag("eabc5b4c-6aff-42f3-b657-3e90cbd00b75", intl["theme_supernatural"]),
            Tag("5fff9cde-849c-4d78-aab0-0d52b2ee1d25", intl["theme_survival"]),
            Tag("292e862b-2d17-4062-90a2-0356caa4ae27", intl["theme_time_travel"]),
            Tag("31932a7e-5b8e-49a6-9f12-2afa39dc544c", intl["theme_traditional_games"]),
            Tag("d7d1730f-6eb0-4ba6-9437-602cac38664c", intl["theme_vampires"]),
            Tag("9438db5a-7e2a-4ac0-b39e-e0d95a34b8a8", intl["theme_video_games"]),
            Tag("d14322ac-4d6f-4e9b-afd9-629d5f4d8a41", intl["theme_villainess"]),
            Tag("8c86611e-fab7-4986-9dec-d1a2f44acdd5", intl["theme_virtual_reality"]),
            Tag("631ef465-9aba-4afb-b0fc-ea10efe274a8", intl["theme_zombies"]),
        )

        return tags.sortIfTranslated(intl)
    }

    // to get all tags from dex https://api.mangadex.org/manga/tag
    internal fun getTags(intl: Intl): List<Tag> {
        return getContents(intl) + getFormats(intl) + getGenres(intl) + getThemes(intl)
    }

    private data class TagMode(val title: String, val value: String) {
        override fun toString(): String = title
    }

    private fun getTagModes(intl: Intl) = arrayOf(
        TagMode(intl["mode_and"], "AND"),
        TagMode(intl["mode_or"], "OR"),
    )

    private class TagInclusionMode(intl: Intl, modes: Array<TagMode>) :
        Filter.Select<TagMode>(intl["included_tags_mode"], modes, 0),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            url.addQueryParameter("includedTagsMode", values[state].value)
        }
    }

    private class TagExclusionMode(intl: Intl, modes: Array<TagMode>) :
        Filter.Select<TagMode>(intl["excluded_tags_mode"], modes, 1),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            url.addQueryParameter("excludedTagsMode", values[state].value)
        }
    }

    private class TagsFilter(intl: Intl, innerFilters: FilterList) :
        Filter.Group<Filter<*>>(intl["tags_mode"], innerFilters),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder, dexLang: String) {
            state.filterIsInstance<UrlQueryFilter>()
                .forEach { filter -> filter.addQueryParameter(url, dexLang) }
        }
    }

    private fun getTagFilters(intl: Intl): FilterList = FilterList(
        TagInclusionMode(intl, getTagModes(intl)),
        TagExclusionMode(intl, getTagModes(intl)),
    )

    internal fun addFiltersToUrl(url: HttpUrl.Builder, filters: FilterList, dexLang: String): HttpUrl {
        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { filter -> filter.addQueryParameter(url, dexLang) }

        return url.build()
    }

    private fun List<Tag>.sortIfTranslated(intl: Intl): List<Tag> = apply {
        if (intl.chosenLanguage == MangaDexIntl.ENGLISH) {
            return this
        }

        return sortedWith(compareBy(intl.collator, Tag::name))
    }
}
