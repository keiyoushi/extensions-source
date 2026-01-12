@file:Suppress("unused")

package eu.kanade.tachiyomi.extension.all.batotov4

import kotlinx.serialization.Serializable

@Serializable
class GraphQLPayload<T>(
    private val variables: T,
    private val query: String,
)

@Serializable
class ApiComicSearchVariables(
    private val select: Select,
) {
    @Serializable
    class Select(
        private val page: Int,
        private val size: Int,
        private val where: String,
        private val word: String,
        private val sortby: String,
        private val incGenres: List<String>,
        private val excGenres: List<String>,
        private val incOLangs: List<String>,
        private val incTLangs: List<String>,
        private val origStatus: String,
        private val siteStatus: String,
        private val chapCount: String,
    )

    constructor(
        pageNumber: Int,
        size: Int,
        sortby: String?,
        query: String = "",
        where: String = "browse",
        incGenres: List<String>? = emptyList(),
        excGenres: List<String>? = emptyList(),
        incOLangs: List<String>? = emptyList(),
        incTLangs: List<String>? = emptyList(),
        origStatus: String? = "",
        siteStatus: String? = "",
        chapCount: String? = "",
    ) : this(
        Select(
            page = pageNumber,
            size = size,
            where = where,
            word = query,
            sortby = sortby ?: "",
            incGenres = incGenres ?: emptyList(),
            excGenres = excGenres ?: emptyList(),
            incOLangs = incOLangs ?: emptyList(),
            incTLangs = incTLangs ?: emptyList(),
            origStatus = origStatus ?: "",
            siteStatus = siteStatus ?: "",
            chapCount = chapCount ?: "",
        ),
    )
}

@Serializable
class ApiComicNodeVariables(
    private val id: String,
)

@Serializable
class ApiChapterListVariables(
    private val comicId: String,
    private val start: Int, // set to -1 to grab all chapters
)

@Serializable
class ApiChapterNodeVariables(
    private val id: String,
)

@Serializable
class HistoryChapterAdd(
    private val comicId: String,
    private val chapterId: String,
)

@Serializable
class ApiMyUpdatesVariables(
    private val select: Select,
) {
    @Serializable
    class Select(
        private val init: Int? = null,
        private val size: Int? = null,
        private val page: Int? = null,
    )

    constructor(
        init: Int? = null,
        size: Int? = null,
        page: Int? = null,
    ) : this(
        Select(
            init = init,
            size = size,
            page = page,
        ),
    )
}

@Serializable
class ApiMyHistoryVariables(
    private val select: Select,
) {
    @Serializable
    data class Select(
        private val start: String? = null,
        private val limit: Int? = null,
    )

    constructor(
        start: String? = null,
        limit: Int? = null,
    ) : this(
        Select(
            start = start,
            limit = limit,
        ),
    )
}

@Serializable
class ApiUserComicListVariables(
    private val select: Select,
) {
    @Serializable
    data class Select(
        private val userId: String,
        private val page: Int? = null,
        private val size: Int? = null,
        private val editor: String? = null,
        private val siteStatus: String? = null,
        private val dbStatus: String? = null,
        private val mod_lock: String? = null,
        private val mod_hide: String? = null,
        private val notUpdatedDays: Int? = null,
        private val scope: String? = null,
    )

    constructor(
        userId: String,
        page: Int? = null,
        size: Int? = null,
        editor: String? = null,
        siteStatus: String? = null,
        dbStatus: String? = null,
        mod_lock: String? = null,
        mod_hide: String? = null,
        notUpdatedDays: Int? = null,
        scope: String? = null,
    ) : this(
        Select(
            userId = userId,
            page = page,
            size = size,
            editor = editor,
            siteStatus = siteStatus,
            dbStatus = dbStatus,
            mod_lock = mod_lock,
            mod_hide = mod_hide,
            notUpdatedDays = notUpdatedDays,
            scope = scope,
        ),
    )
}
