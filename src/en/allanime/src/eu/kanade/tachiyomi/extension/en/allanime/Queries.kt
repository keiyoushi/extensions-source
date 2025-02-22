package eu.kanade.tachiyomi.extension.en.allanime
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

val POPULAR_QUERY: String = buildQuery {
    """
        query (
            %type: VaildPopularTypeEnumType!
            %size: Int!
            %page: Int
            %dateRange: Int
            %allowAdult: Boolean
            %allowUnknown: Boolean
        ) {
            queryPopular(
                type: %type
                size: %size
                dateRange: %dateRange
                page: %page
                allowAdult: %allowAdult
                allowUnknown: %allowUnknown
            ) {
                recommendations {
                    anyCard {
                        _id
                        name
                        thumbnail
                        englishName
                    }
                }
            }
        }
    """
}

val SEARCH_QUERY: String = buildQuery {
    """
        query (
            %search: SearchInput
            %size: Int
            %page: Int
            %translationType: VaildTranslationTypeMangaEnumType
            %countryOrigin: VaildCountryOriginEnumType
        ) {
            mangas(
                search: %search
                limit: %size
                page: %page
                translationType: %translationType
                countryOrigin: %countryOrigin
            ) {
                edges {
                    _id
                    name
                    thumbnail
                    englishName
                }
            }
        }
    """
}

val DETAILS_QUERY: String = buildQuery {
    """
        query (%id: String!) {
            manga(_id: %id) {
                _id
                name
                thumbnail
                description
                authors
                genres
                tags
                status
                altNames
                englishName
            }
        }
    """
}

val CHAPTERS_QUERY: String = buildQuery {
    """
        query (%id: String!, %chapterNumStart: Float!, %chapterNumEnd: Float!) {
            episodeInfos(
                showId: %id
                episodeNumStart: %chapterNumStart
                episodeNumEnd: %chapterNumEnd
            ) {
                episodeIdNum
                notes
                uploadDates
            }
        }
    """
}

val PAGE_QUERY: String = buildQuery {
    """
        query (
            %id: String!
            %translationType: VaildTranslationTypeMangaEnumType!
            %chapterNum: String!
        ) {
            chapterPages(
                mangaId: %id
                translationType: %translationType
                chapterString: %chapterNum
            ) {
                edges {
                    pictureUrls
                    pictureUrlHead
                }
            }
        }
    """
}
