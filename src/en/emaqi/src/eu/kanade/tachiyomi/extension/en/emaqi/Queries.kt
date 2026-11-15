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
        slug
        title
        cover {
          url
        }
      }
    }
""".trimIndent()

val COMIC_QUERY = $$"""
    query FetchComicData($comicId: String!) {
      comicVolumes(comicId: $comicId) {
        comic {
          slug
          title
          synopsis
          rating
          creators
          publisher
          completed
          cover {
            url
          }
          genres {
            ... on Tag {
              name
            }
          }
        }
        volumes {
          comicId
          volumeNumber
          eisbn
          slug
          name
          trialPage
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
