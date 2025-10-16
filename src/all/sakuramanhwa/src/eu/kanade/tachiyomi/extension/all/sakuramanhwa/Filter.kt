package eu.kanade.tachiyomi.extension.all.sakuramanhwa

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

internal const val GroupTypeNone = -1
internal const val GroupTypeSearch = 0
internal const val GroupTypeLatesUpdates = 1

internal class GroupFilter(
    i18n: I18nDictionary,
) : Filter.Select<String>(
    "",
    arrayOf(
        i18n.library.title,
        i18n.home.lastUpdatesNormal,
    ),
) {
    fun setUrlPath(builder: Builder): Int {
        val path = when (state) {
            GroupTypeSearch -> "/v1/manga"
            GroupTypeLatesUpdates -> "/v1/manga/search/latesUpdates"
            else -> {
                throw UnsupportedOperationException()
            }
        }
        builder.encodedPath(path)
        return state
    }
}

internal open class UrlFilter(
    title: String,
    private val requireState: Int,
    val lis: List<List<String>>,
) : Filter.Select<String>(
    title,
    lis.map { it[0] }.toTypedArray(),
) {
    fun checkGroupState(groupState: Int): Boolean {
        if (state != 0 && (requireState == GroupTypeNone || requireState == groupState)) {
            return true
        }
        state = 0
        return false
    }
}

internal class CategoryFilter(
    i18n: I18nDictionary,
    lis: List<List<String>> = listOf(
        listOf(i18n.home.updates.buttons.genres["all"]!!, "author", ""),
        listOf(i18n.home.updates.buttons.genres["mature"]!!, "author", "mature"),
        listOf(i18n.home.updates.buttons.genres["normal"]!!, "author", "normal"),
    ),
) : UrlFilter(
    i18n.library.filter["category"]!!,
    GroupTypeNone,
    lis,
) {
    fun setUrlParam(builder: Builder, groupState: Int) {
        if (!checkGroupState(groupState)) {
            return
        }
        if (groupState == GroupTypeSearch) {
            builder.setQueryParameter("${lis[state][1]}[]", lis[state][2])
        } else {
            builder.setQueryParameter(lis[state][1], lis[state][2])
        }
    }
}

internal class SortFilter(
    i18n: I18nDictionary,
    lis: List<List<String>> = listOf(
        listOf("_", "sort", ""),
        listOf("${i18n.library.sort["title"]!!}⬇️", "sort", "title"),
        listOf("${i18n.library.sort["title"]!!}⬆️", "sort", "title"),
        listOf("${i18n.library.sort["type"]!!}⬇️", "sort", "type"),
        listOf("${i18n.library.sort["type"]!!}⬆️", "sort", "type"),
        listOf("${i18n.library.sort["rating"]!!}⬇️", "sort", "rating"),
        listOf("${i18n.library.sort["rating"]!!}⬆️", "sort", "rating"),
        listOf("${i18n.library.sort["date"]!!}⬇️", "sort", "create_at"),
        listOf("${i18n.library.sort["date"]!!}⬆️", "sort", "create_at"),
    ),
) : UrlFilter(
    i18n.library.filter["sortBy"]!!,
    GroupTypeSearch,
    lis,
) {
    fun setUrlParam(builder: Builder, groupState: Int) {
        if (!checkGroupState(groupState)) {
            return
        }
        builder.setQueryParameter(lis[state][1], lis[state][2])
        builder.setQueryParameter("order", if (state % 2 == 1) "desc" else "asc")
    }
}

internal class LanguageCheckBoxFilter(name: String, val key: String) : Filter.CheckBox(name) {
    override fun toString(): String {
        return key
    }
}

internal class LanguageCheckBoxFilterGroup(
    i18n: I18nDictionary,
    data: LinkedHashMap<String, String> = linkedMapOf(
        i18n.home.updates.buttons.language["all"]!! to "",
        i18n.home.updates.buttons.language["spanish"]!! to "esp",
        i18n.home.updates.buttons.language["english"]!! to "eng",
        i18n.home.updates.buttons.language["chinese"]!! to "ch",
        i18n.home.updates.buttons.language["raw"]!! to "raw",
    ),
) : Filter.Group<LanguageCheckBoxFilter>(
    i18n.library.filter["language"]!!,
    data.map { (k, v) ->
        LanguageCheckBoxFilter(k, v)
    },
) {
    fun setUrlParam(builder: Builder, groupState: Int) {
        if (state[0].state) {
            // clear
            state.forEach { it.state = false }
            return
        }
        var langParam = false
        state.forEach {
            if (it.state) {
                if (groupState == GroupTypeSearch) {
                    builder.addQueryParameter("language[]", it.toString())
                } else {
                    if (langParam) {
                        it.state = false
                    } else {
                        builder.addQueryParameter("language", it.toString())
                        langParam = true
                    }
                }
            }
        }
    }
}
