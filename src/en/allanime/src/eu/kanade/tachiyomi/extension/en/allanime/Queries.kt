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

const val UPDATE_QUERY: String = $$"""
    query ($id: String!, $showId: String!) {
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
            malId
            aniListId
            relatedMangas
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

const val RELATED_QUERY: String = $$"""
    query (
        $ids: [String!]!
        $search: SearchInput
        $fewerGenresSearch: SearchInput
        $size: Int
        $translationType: VaildTranslationTypeMangaEnumType
    ) {
      mangas(
          search: $search
          limit: $size
          translationType: $translationType
      ) {
        edges {
          _id
          name
          thumbnail
          englishName
        }
      }
      fewerGenresSearch: mangas(
          search: $fewerGenresSearch
          limit: $size
          translationType: $translationType
      ) {
        edges {
          _id
          name
          thumbnail
          englishName
        }
      }
      mangasWithIds(ids: $ids) {
        _id
        name
        thumbnail
        englishName
      }
    }
"""
