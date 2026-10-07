package eu.kanade.tachiyomi.extension.ja.mangaboxme

val RANKING_QUERY = """
    query Ranking {
      featured {
        sections {
          ... on RankingSection {
            items {
              mangaID
              title
            }
          }
        }
      }
    }
""".trimIndent()

val NEW_ARRIVALS_QUERY = """
    query NewArrivals {
      featured {
        sections {
          ... on NewArrivalsSection {
            items {
              mangaID
              title
            }
          }
        }
      }
    }
""".trimIndent()

val SEARCH_QUERY = """
    query Search {
      honshiMangas {
        mangaID
        title
        searchKeywords
      }
    }
""".trimIndent()
