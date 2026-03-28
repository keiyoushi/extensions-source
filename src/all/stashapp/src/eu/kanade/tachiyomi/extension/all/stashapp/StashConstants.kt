package eu.kanade.tachiyomi.extension.all.stashapp

const val PREF_BASE_URL = "base_url"

const val DEFAULT_BASE_URL = "http://localhost:9999"

const val PREF_API_KEY = "api_key"

const val MANGA_BRIEF_PER_PAGE = 25

const val MANGA_BRIEF_QUERY = $$"""
    query MangaBrief($filter: FindFilterType) {
        findGalleries(filter: $filter) {
            galleries {
                id
                title
                folder {
                    path
                }
                cover {
                    paths {
                        thumbnail
                    }
                    visual_files {
                        __typename
                    }
                }
            }
        }
    }
"""

const val MANGA_DETAILS_QUERY = $$"""
    query MangaDetails($id: ID!) {
        findGallery(id: $id) {
              id
              title
              folder {
                  path
              }
              photographer
              details
              tags {
                  name
              }
              cover {
                  paths {
                      thumbnail
                  }
                  visual_files {
                      __typename
                  }
              }
        }
    }
"""

const val CHAPTER_LIST_QUERY = $$"""
    query ChapterList($id: ID!) {
        findGallery(id: $id) {
            id
            created_at
            photographer
        }
    }
"""

const val PAGE_LIST_QUERY = $$"""
    query PageList($id: Int!) {
        findImages(
            filter: { per_page: -1, sort: "path" }
            image_filter: {
                galleries_filter: { id: { value: $id, modifier: EQUALS } }
                files_filter: { image_file_filter: { format: { value: "", modifier: NOT_EQUALS } } }
            }
        ) {
            images {
                id
            }
        }
    }
"""
