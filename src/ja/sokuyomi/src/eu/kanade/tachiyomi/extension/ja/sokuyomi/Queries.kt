package eu.kanade.tachiyomi.extension.ja.sokuyomi

val SERIES_QUERY = $$"""
        query ListTitle($perPage: Int!, $pageNumber: Int!, $field: PostOrderFields!, $isAdult: Boolean) {
          listTitle(
            input: {is_adult: {eq: $isAdult}}
            page: {perPage: $perPage, pageNumber: $pageNumber}
            orderBy: {field: $field, sort: DESC}
          ) {
            pageInfo {
              totalCount
              totalPage
              currentPage
            }
            edges {
              node {
                name
                slug
                title_cover {
                  key
                }
              }
            }
          }
        }
""".trimIndent()

val SEARCH_QUERY = $$"""
        query ListTitle($name: String, $authorName: String, $tagName: String, $perPage: Int!, $pageNumber: Int!, $field: PostOrderFields!, $isAdult: Boolean) {
          listTitle(
            input: {name: {contains: $name}, author_name: {contains: $authorName}, tag_name: {contains: $tagName}, is_adult: {eq: $isAdult}}
            page: {perPage: $perPage, pageNumber: $pageNumber}
            orderBy: {field: $field, sort: DESC}
          ) {
            pageInfo {
              totalCount
              totalPage
              currentPage
            }
            edges {
              node {
                name
                slug
                title_cover {
                  key
                }
              }
            }
          }
        }
""".trimIndent()

val TAG_FILTER_QUERY = $$"""
    query ListTitleByTag($tag_slug: String!, $perPage: Int!, $pageNumber: Int!) {
      listTitle(
        input: {tag_slug: {eq: $tag_slug}}
        page: {perPage: $perPage, pageNumber: $pageNumber}
        orderBy: {field: LIKE_COUNT, sort: ASC}
      ) {
        pageInfo {
          totalCount
          totalPage
          currentPage
        }
        edges {
          node {
            ...TitleFragment
            title_cover {
              key
            }
          }
        }
      }
    }

    fragment TitleFragment on Title {
      name
      slug
    }
""".trimIndent()

val DETAILS_QUERY = $$"""
        query GetTitle($titleSlug: String!) {
          getTitle(input: {slug: {eq: $titleSlug}}) {
            ...TitleFragment
            label {
              publisher {
                name
              }
            }
            genre {
              name
            }
            title_cover {
              origin_key
            }
            authors {
              name
            }
            tags {
              name
            }
          }
        }

        fragment TitleFragment on Title {
          name
          name_hiragana
          name_katakana
          description
          is_adult
          is_finished
        }
""".trimIndent()

val CHAPTER_LIST_QUERY = $$"""
        query ListVolume($titleSlug: String, $perPage: Int!, $pageNumber: Int!, $sort: PostOrderSorts!) {
          listVolume(
            input: {title_slug: {eq: $titleSlug}}
            page: {perPage: $perPage, pageNumber: $pageNumber}
            orderBy: {field: VOLUME_NUMBER, sort: $sort}
          ) {
            edges {
              node {
                ...VolumeFragment
                volume_consumption_coin {
                  id
                  consumption_coin
                }
              }
            }
          }
        }

        fragment VolumeFragment on Volume {
          volume_number
          name
          description
          consumption_coin
          opend_at
          slug
          is_purchase
          is_available_for_sale
        }
""".trimIndent()

val VIEWER_QUERY = $$"""
        query GetVolumeViewer($volumeSlug: String!) {
          getVolumeViewer(input: {volume_slug: {eq: $volumeSlug}}) {
            volume_pages {
              ...VolumePageFragment
            }
          }
        }

        fragment VolumePageFragment on VolumePage {
          page_number
          key
        }
""".trimIndent()

val LOGIN_QUERY = $$"""
    mutation Signin($mail_address: String!, $password: String!) {
      signin(
        input: {mail_address: $mail_address, password: $password}
      ) {
        access_token
        refresh_token
      }
    }
""".trimIndent()

val REFRESH_QUERY = $$"""
    mutation Token($refresh_token: String!) {
      token(input: {refresh_token: {eq: $refresh_token}}) {
        access_token
        refresh_token
      }
    }
""".trimIndent()
