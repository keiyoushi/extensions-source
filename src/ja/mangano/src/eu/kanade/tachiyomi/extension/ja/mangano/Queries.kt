package eu.kanade.tachiyomi.extension.ja.mangano

val POPULAR_QUERY = """
    query RankingsMonthly {
      ranking {
        monthly2(first: 100) {
          edges {
            node {
              id
              title
              coverImage {
                url
              }
            }
          }
        }
      }
    }
""".trimIndent()

val LATEST_QUERY = $$"""
    query NewWorks($after: String) {
      newWorks2(first: 100, after: $after) {
        edges {
          node {
            id
            title
            coverImage {
              url
            }
          }
        }
        pageInfo {
          endCursor
          hasNextPage
        }
      }
    }
""".trimIndent()

val SEARCH_QUERY = $$"""
    query Search($keyword: String!, $after: String) {
      search(
        keyword: $keyword
        first: 50
        after: $after
      ) {
        edges {
          cursor
          node {
            ... on Work {
              id
              title
              coverImage {
                url
              }
            }
          }
        }
        pageInfo {
          endCursor
          hasNextPage
        }
      }
    }
""".trimIndent()

val TAG_QUERY = $$"""
    query Tag($title: String!, $first: Int!, $after: String) {
      tag(title: $title) {
        works(first: $first, after: $after) {
          edges {
            node {
              id
              title
              coverImage {
                url
              }
            }
          }
          pageInfo {
            endCursor
            hasNextPage
          }
        }
      }
    }
""".trimIndent()

val DETAILS_QUERY = $$"""
    query MangaDetails($id: ID!) {
      node(id: $id) {
        ... on Work {
          id
          title
          description
          isCompleted
          coverImage {
            url
          }
          user {
            displayName
          }
          tags {
            title
          }
        }
      }
    }
""".trimIndent()

val CHAPTER_LIST_QUERY = $$"""
    query ChapterList($id: ID!) {
      node(id: $id) {
        ... on Work {
          episodes(first: 1000) {
            edges {
              node {
                id
                title
                number
                publishedAt
                salesInfo {
                  pagesChargedFrom
                }
                purchasedByViewer
                canViewerSkipPaywall
              }
            }
          }
        }
      }
    }
""".trimIndent()

val VIEWER_QUERY = $$"""
    query GetEpisode($id: ID!) {
      node(id: $id) {
        ... on Episode {
          allPagesConnection: pages(first: 2000) {
            edges {
              node {
                image {
                  url
                }
              }
            }
          }
        }
      }
    }
""".trimIndent()
