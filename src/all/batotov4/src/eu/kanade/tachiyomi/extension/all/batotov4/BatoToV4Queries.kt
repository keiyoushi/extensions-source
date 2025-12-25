package eu.kanade.tachiyomi.extension.all.batotov4

import eu.kanade.tachiyomi.extension.all.batotov4.BatoToV4.Companion.whitespace

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
        .replace(whitespace, " ")
}

// Search query for comics
val SEARCH_COMIC_QUERY: String = buildQuery {
    """
        query get_search_comic(%select: Search_Comic_Select) {
            get_search_comic(select: %select) {
                req_page req_size req_word
                new_page
                paging {
                    total pages page init size skip limit prev next
                }
                items {
                    id data {
                        id dbStatus isPublic name
                        origLang tranLang
                        urlPath urlCover600 urlCoverOri
                        genres altNames authors artists
                        is_hot is_new sfw_result
                        score_val follows reviews comments_total
                        chapterNode_up_to {
                            id data {
                                id dateCreate
                                dbStatus isFinal sfw_result
                                dname urlPath is_new
                                userId userNode {
                                    id data {
                                        id name uniq avatarUrl urlPath
                                    }
                                }
                            }
                        }
                    }
                    sser_follow
                    sser_lastReadChap {
                        date chapterNode {
                            id data {
                                id dbStatus isFinal sfw_result
                                dname urlPath is_new
                                userId userNode {
                                    id data {
                                        id name uniq avatarUrl urlPath
                                    }
                                }
                            }
                        }
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
                id data {
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
