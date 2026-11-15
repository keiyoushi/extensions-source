package eu.kanade.tachiyomi.extension.ja.sokuyomi

val LIST_QUERY = $$"""
        query ListTitle($keyword: String, $tagSlug: String, $perPage: Int!, $pageNumber: Int!, $field: PostOrderFields!, $sort: PostOrderSorts!) {
          listTitle(
            input: {name: {contains: $keyword}, author_name: {contains: $keyword}, tag_name: {contains: $keyword}, tag_slug: {eq: $tagSlug}}
            page: {perPage: $perPage, pageNumber: $pageNumber}
            orderBy: {field: $field, sort: $sort}
          ) {
            pageInfo {
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

val DETAILS_QUERY = $$"""
        query GetTitle($titleSlug: String!) {
          getTitle(input: {slug: {eq: $titleSlug}}) {
            name
            name_hiragana
            name_katakana
            description
            is_adult
            is_finished
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
          listVolume(
            input: {title_slug: {eq: $titleSlug}}
            page: {perPage: 1000, pageNumber: 0}
            orderBy: {field: VOLUME_NUMBER, sort: DESC}
          ) {
            edges {
              node {
                volume_number
                name
                consumption_coin
                opend_at
                slug
                is_purchase
                is_available_for_sale
                volume_consumption_coin {
                  consumption_coin
                }
              }
            }
          }
          listChapter(
            input: {title_slug: {eq: $titleSlug}}
            page: {perPage: 1000, pageNumber: 0}
            orderBy: {field: CHAPTER_NUMBER, sort: DESC}
          ) {
            edges {
              node {
                chapter_number
                name
                consumption_coin
                opend_at
                slug
                is_purchase
                is_available_for_sale
                chapter_consumption_coin {
                  consumption_coin
                }
              }
            }
          }
        }
""".trimIndent()

val VOLUME_VIEWER_QUERY = $$"""
        query GetVolumeViewer($slug: String!) {
          getVolumeViewer(input: {volume_slug: {eq: $slug}}) {
            volume_pages {
              page_number
              key
            }
          }
        }
""".trimIndent()

val CHAPTER_VIEWER_QUERY = $$"""
        query GetChapterViewer($slug: String!) {
          getChapterViewer(input: {chapter_slug: {eq: $slug}}) {
            chapter_pages {
              page_number
              key
            }
          }
        }
""".trimIndent()

val LOGIN_QUERY = $$"""
    mutation Signin($mail_address: String!, $password: String!) {
      signin(
        input: {mail_address: $mail_address, password: $password}
      ) {
        access_token
        refresh_token
        expires_at
      }
    }
""".trimIndent()

val REFRESH_QUERY = $$"""
    mutation Token($refresh_token: String!) {
      token(input: {refresh_token: {eq: $refresh_token}}) {
        access_token
        refresh_token
        expires_at
      }
    }
""".trimIndent()
