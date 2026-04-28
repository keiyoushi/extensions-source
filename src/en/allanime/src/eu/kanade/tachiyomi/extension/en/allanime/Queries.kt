package eu.kanade.tachiyomi.extension.en.allanime

const val POPULAR_QUERY: String = $$"""
    query (
        $type: VaildPopularTypeEnumType!
        $size: Int!
        $page: Int
        $dateRange: Int
        $allowAdult: Boolean
        $allowUnknown: Boolean
    ) {
        queryPopular(
            type: $type
            size: $size
            dateRange: $dateRange
            page: $page
            allowAdult: $allowAdult
            allowUnknown: $allowUnknown
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

const val SEARCH_QUERY: String = $$"""
    query (
        $search: SearchInput
        $size: Int
        $page: Int
        $translationType: VaildTranslationTypeMangaEnumType
        $countryOrigin: VaildCountryOriginEnumType
    ) {
        mangas(
            search: $search
            limit: $size
            page: $page
            translationType: $translationType
            countryOrigin: $countryOrigin
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

const val DETAILS_QUERY: String = $$"""
    query ($id: String!) {
        manga(_id: $id) {
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

const val CHAPTERS_QUERY: String = $$"""
    query ($id: String!, $showId: String!) {
        manga(_id: $id) {
            _id
            name
            availableChaptersDetail
        }
        episodeInfos(
            showId: $showId
            episodeNumStart: 0
            episodeNumEnd: 9999
        ) {
            episodeIdNum
            notes
            uploadDates
        }
    }
"""

const val PAGE_QUERY: String = $$"""
    query (
        $id: String!
        $translationType: VaildTranslationTypeMangaEnumType!
        $chapterNum: String!
    ) {
        chapterPages(
            mangaId: $id
            translationType: $translationType
            chapterString: $chapterNum
        ) {
            edges {
                pictureUrls
                pictureUrlHead
            }
        }
    }
"""
