package eu.kanade.tachiyomi.multisrc.gigaviewer

private val SERIES_LIST_ITEM_FRAGMENT = """
    fragment SeriesListItem on Series {
      databaseId
      title
      thumbnailUri
      firstEpisode {
        permalink
      }
      firstVolume {
        permalink
      }
      latestEpisode {
        publishedAt
      }
      latestVolume {
        thumbnailUri
      }
      likeCount
    }
""".trimIndent()

private val SERIES_LIST_FRAGMENT = """
    fragment SeriesList on Node {
      ... on SerialGroup {
        series {
          edges {
            node {
              ...SeriesListItem
            }
          }
        }
      }
      ... on Genre {
        series {
          edges {
            node {
              ...SeriesListItem
            }
          }
        }
      }
    }
""".trimIndent() + "\n" + SERIES_LIST_ITEM_FRAGMENT

val SEARCH_QUERY = $$"""
    query Search($keyword: String!) {
      searchSeries(keyword: $keyword) {
        edges {
          node {
            ...SeriesListItem
          }
        }
      }
    }
""".trimIndent() + "\n" + SERIES_LIST_ITEM_FRAGMENT

val SERIES_QUERY = $$"""
    query Series($id: String!) {
      series(databaseId: $id) {
        databaseId
        title
        author {
          name
        }
        description
        thumbnailUri
        firstEpisode {
          permalink
        }
        firstVolume {
          permalink
        }
        latestVolume {
          thumbnailUri
        }
        episodes: readableProducts(types: [EPISODE], first: 0) {
          totalCount
        }
        volumes: readableProducts(types: [VOLUME], first: 0) {
          totalCount
        }
      }
    }
""".trimIndent()

fun seriesListQuery(variables: Set<String>): String {
    val definitions = variables.joinToString { $$"$$$it: ID!" }
    val nodes = variables.joinToString(" ") { $$"$$it: node(id: $$$it) { ...SeriesList }" }
    return "query($definitions) { $nodes }\n$SERIES_LIST_FRAGMENT"
}
