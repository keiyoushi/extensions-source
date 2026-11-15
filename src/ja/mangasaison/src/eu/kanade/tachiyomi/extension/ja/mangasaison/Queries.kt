package eu.kanade.tachiyomi.extension.ja.mangasaison

val POPULAR_QUERY = $$"""
    query storeLatestWeeklySalesRankings($genreIds: [String], $limit: Int!) {
      storeLatestWeeklySalesRankings(genreIds: $genreIds, limit: $limit) {
        ranking {
          titleId
          title {
            titleName
            compressedTitleThumbnailPath
          }
        }
      }
    }
""".trimIndent()

val LATEST_QUERY = $$"""
    query newArrivalContents($layoutId: ID!, $limit: Int!, $offset: Int) {
      newArrivalContents(layoutId: $layoutId, limit: $limit, offset: $offset) {
        titleId
        contentName
        compressedContentThumbnailPath
      }
    }
""".trimIndent()

val DETAILS_QUERY = $$"""
    query bookTitleDetail($titleId: Int!, $limit: Int!, $sortType: String) {
      bookTitle(titleId: $titleId) {
        titleName
        titleNameKana
        compressedTitleThumbnailPath
        publisherName
        magazineName
        longDescription
        authorNames
        genres {
          genre {
            name
          }
        }
        hasLastVolume
      }
      bookContents(titleId: $titleId, limit: $limit, sortType: $sortType) {
        distributionId
        contentName
        volumeNo
        salesStartAt
        sampleDistributionId
        currentPrice
        limitedReadPeriodBookContent {
          distributionId
        }
        isPurchased
      }
    }
""".trimIndent()
