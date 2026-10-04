package eu.kanade.tachiyomi.extension.ja.jumptoon

val RANKING_QUERY = """
    query SeriesOverallRankingPage {
      rankingFeedV2(rankingFeedType: OVERALL_V2) {
        seriesList(publishDestinationType: WEB, first: 30) {
          series {
            id
            name
            seriesThumbnailV2ImageUrl
          }
        }
      }
    }
""".trimIndent()

val LATEST_QUERY = """
    query TopPageData {
      dailyUpdatedSeriesFeedList(publishDestinationType: WEB, type: UPDATED_SERIES_FEED) {
        rankedSeriesList {
          series {
            id
            name
            seriesThumbnailV2ImageUrl
          }
        }
      }
    }
""".trimIndent()

val SEARCH_QUERY = $$"""
    query SearchSeries($q: String!, $offset: Int, $first: Int) {
      searchSeries(q: $q, offset: $offset, first: $first, publishDestinationType: WEB) {
        totalCount
        seriesList {
          id
          name
          seriesThumbnailV2ImageUrl
          pairedSeries {
            id
            name
            seriesThumbnailV2ImageUrl
          }
        }
      }
    }
""".trimIndent()

val DETAILS_QUERY = $$"""
    query SeriesPageData($seriesId: ID!, $hasSession: Boolean!) {
      series(id: $seriesId) {
        name
        description
        copyright
        seriesThumbnailV2ImageUrl
        seriesStatusType
        isSuspended
        genreTypes
        authorList {
          author {
            name
          }
        }
      }
      seriesEpisodeList(seriesId: $seriesId, orderDirectionType: DESC) {
        edges {
          node {
            id
            seriesId
            number
            notation
            title
            publishStartDatetime
            offerType
            userSeriesEpisode @include(if: $hasSession) {
              isPurchased
              rentalFinishedAt
            }
          }
        }
      }
      seriesComicsList(seriesId: $seriesId, orderDirectionType: DESC) {
        edges {
          node {
            id
            seriesId
            number
            notation
            publishStartDatetime
            previewPageCount
            userSeriesComics {
              isPurchased
            }
          }
        }
      }
    }
""".trimIndent()

val CONTENT_QUERY = $$"""
    query SeriesEpisodePageData($seriesId: ID!, $id: IntID!) {
      content: seriesEpisodeContent(id: $id, seriesId: $seriesId) {
        seriesId
        number
        scrambleAlgorithmType
        pageList {
          width
          imageUrl
        }
      }
    }
""".trimIndent()

val COMICS_QUERY = $$"""
    query SeriesComicsViewerContent($seriesId: ID!, $id: IntID!) {
      content: seriesComicsContent(id: $id, seriesId: $seriesId) {
        seriesId
        number
        scrambleAlgorithmType
        pageList {
          width
          imageUrl
        }
      }
    }
""".trimIndent()

val PREVIEW_QUERY = $$"""
    query SeriesComicsTrialPreviewContent($seriesId: ID!, $id: IntID!) {
      content: seriesComicsPreviewContent(id: $id, seriesId: $seriesId) {
        seriesId
        number
        scrambleAlgorithmType
        pageList {
          width
          imageUrl
        }
      }
    }
""".trimIndent()
