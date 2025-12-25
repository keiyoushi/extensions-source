package eu.kanade.tachiyomi.extension.all.batotov4

import eu.kanade.tachiyomi.extension.all.batotov4.BatoToV4.Companion.whitespace

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
        .replace(whitespace, " ")
}

val COMIC_SEARCH_QUERY: String = buildQuery {
    """
        query (%select: Comic_Browse_Select) {
            get_comic_browse(select: %select) {
                paging {
                    pages
                    page
                    next
                }
                items {
                    data {
                        id
                        name
                        urlPath
                        urlCover300
                        urlCover600
                        urlCover900
                        urlCoverOri
                    }
                }
            }
        }
    """
}

// Chapter list query
val CHAPTER_LIST_QUERY: String = buildQuery {
    """
        query get_comic_chapterList(%comicId: ID!, %start: Int) {
            get_comic_chapterList(comicId: %comicId, start: %start) {
                data {
                    id
                    dname
                    title
                    urlPath
                    dateCreate
                    dateModify
                }
            }
        }
    """
}

// Chapter node query (for page images)
val CHAPTER_NODE_QUERY: String = buildQuery {
    """
        query get_chapterNode(%id: ID!) {
            get_chapterNode(id: %id) {
                data {
                    imageFile {
                        urlList
                    }
                }
            }
        }
    """
}

// Comic browse pager query (for pagination info)
val COMIC_BROWSE_PAGER_QUERY: String = buildQuery {
    """
        query get_comic_browse_pager(%select: Comic_Browse_Select) {
            get_comic_browse_pager(select: %select) {
                total pages page init size skip limit prev next
            }
        }
    """
}
