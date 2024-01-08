package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element

/*
 * ManaToki is too big to support in a Factory File., So split into separate file.
 */

object ManaToki : NewToki("ManaToki", "comic", manaTokiPreferences) {
    // / ! DO NOT CHANGE THIS !  Only the site name changed from newtoki.
    override val id = MANATOKI_ID

    override val baseUrl get() = "https://$MANATOKI_PREFIX$domainNumber.net"

    private val chapterRegex by lazy { Regex(""" [ \d,~.-]+화$""") }

    fun latestUpdatesElementParse(element: Element): SManga {
        val linkElement = element.select("a.btn-primary")
        val rawTitle = element.select(".post-subject > a").first()!!.ownText().trim()

        val title = rawTitle.trim().replace(chapterRegex, "")

        val manga = SManga.create()
        manga.url = getUrlPath(linkElement.attr("href"))
        manga.title = title
        manga.thumbnail_url = element.select(".img-item > img").attr("src")
        manga.initialized = false
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = ("$baseUrl/comic" + (if (page > 1) "/p$page" else "")).toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SearchPublishTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("publish", filter.values[filter.state])
                    }
                }

                is SearchJaumTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("jaum", filter.values[filter.state])
                    }
                }

                is SearchGenreTypeList -> {
                    val genres = filter.state.filter { it.state }.joinToString(",") { it.id }
                    url.addQueryParameter("tag", genres)
                }

                is SearchSortTypeList -> {
                    val state = filter.state ?: return@forEach
                    url.addQueryParameter("sst", arrayOf("wr_datetime", "wr_hit", "wr_good", "as_update")[state.index])
                    url.addQueryParameter("sod", if (state.ascending) "asc" else "desc")
                }

                else -> {}
            }
        }

        if (query.isNotBlank()) {
            url.addQueryParameter("stx", query)

            // Remove some filter QueryParams that not working with query
            url.removeAllQueryParameters("publish")
            url.removeAllQueryParameters("jaum")
            url.removeAllQueryParameters("tag")
        }

        return GET(url.toString(), headers)
    }

    private class SearchCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    // [...document.querySelectorAll("form.form td")[3].querySelectorAll("span.btn")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchPublishTypeList : Filter.Select<String>(
        "Publish",
        arrayOf(
            "전체",
            "주간",
            "격주",
            "월간",
            "단편",
            "단행본",
            "완결",
        ),
    )

    // [...document.querySelectorAll("form.form td")[4].querySelectorAll("span.btn")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchJaumTypeList : Filter.Select<String>(
        "Jaum",
        arrayOf(
            "전체",
            "ㄱ",
            "ㄴ",
            "ㄷ",
            "ㄹ",
            "ㅁ",
            "ㅂ",
            "ㅅ",
            "ㅇ",
            "ㅈ",
            "ㅊ",
            "ㅋ",
            "ㅌ",
            "ㅍ",
            "ㅎ",
            "0-9",
            "a-z",
        ),
    )

    // [...document.querySelectorAll("form.form td")[6].querySelectorAll("span.btn")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchGenreTypeList : Filter.Group<SearchCheckBox>(
        "Genres",
        arrayOf(
            "전체",
            "17",
            "BL",
            "SF",
            "TS",
            "개그",
            "게임",
            "도박",
            "드라마",
            "라노벨",
            "러브코미디",
            "먹방",
            "백합",
            "붕탁",
            "순정",
            "스릴러",
            "스포츠",
            "시대",
            "애니화",
            "액션",
            "음악",
            "이세계",
            "일상",
            "전생",
            "추리",
            "판타지",
            "학원",
            "호러",
        ).map { SearchCheckBox(it) },
    )

    private class SearchSortTypeList : Filter.Sort(
        "Sort",
        arrayOf(
            "기본(날짜순)",
            "인기순",
            "추천순",
            "업데이트순",
        ),
    )

    override fun getFilterList() = FilterList(
        SearchSortTypeList(),
        Filter.Separator(),
        Filter.Header(ignoredForTextSearch()),
        SearchPublishTypeList(),
        SearchJaumTypeList(),
        SearchGenreTypeList(),
    )
}
