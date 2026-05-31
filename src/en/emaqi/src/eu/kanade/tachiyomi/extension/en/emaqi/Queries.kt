package eu.kanade.tachiyomi.extension.en.emaqi

val SERIES_QUERY = $$"""
    query FetchHomeSection($slug: String!, $mangaAfter: String) {
      homeSection(slug: $slug) {
        mangaConn(first: 40, after: $mangaAfter) {
          edges {
            node {
              comic {
                comicId
                slug
                title
                cover {
                  url
                }
              }
            }
          }
          pageInfo {
            hasNextPage
            endCursor
          }
        }
      }
    }
""".trimIndent()

val SEARCH_QUERY = $$"""
    query Search($input: SearchInput!) {
      search(input: $input) {
        comicId
        title
        slug
        cover {
            url
        }
      }
    }
""".trimIndent()

val GENRE_QUERY = $$"""
    query FetchGenre($slug: String!, $mangaAfter: String) {
      genre(slug: $slug) {
        mangaConn(first: 40, after: $mangaAfter) {
          edges {
            node {
              comic {
                comicId
                slug
                title
                cover {
                  url
                }
              }
            }
          }
          pageInfo {
            hasNextPage
            endCursor
          }
        }
      }
    }
""".trimIndent()

val DETAILS_QUERY = $$"""
    query FetchMangaStatus($comicId: String!) {
      manga(comicId: $comicId) {
        comic {
          title
          synopsis
          rating
          creators
          publisher
          metadata {
            completed
          }
          cover {
            url
          }
          genres {
            ... on Tag {
              name
            }
          }
        }
      }
    }
""".trimIndent()

val CHAPTER_LIST_QUERY = $$"""
    query FetchComicData($comicId: String!) {
      comicVolumes(comicId: $comicId) {
        volumes {
          comicId
          trialPage
          slug
          volumeNumber
          name
          price
          purchased
          free
          releasesAt
        }
      }

      chapters(comicId: $comicId) {
        comicId
        chapterNumber
        name
        purchased
        free
        releasesAt
      }
    }
""".trimIndent()

val CHAPTER_QUERY = $$"""
    query FetchChapterContents($comicId: String!, $chapterNumber: Int!) {
      chapter(comicId: $comicId, chapterNumber: $chapterNumber) {
        contents {
          pages {
            url
          }
          hash
        }
      }
    }
""".trimIndent()

val VOLUME_QUERY = $$"""
    query FetchMangaContents($comicId: String!, $volumeNumber: Int!) {
      manga(comicId: $comicId, volumeNumber: $volumeNumber) {
        contents {
          pages {
            url
          }
          hash
        }
      }
    }
""".trimIndent()
