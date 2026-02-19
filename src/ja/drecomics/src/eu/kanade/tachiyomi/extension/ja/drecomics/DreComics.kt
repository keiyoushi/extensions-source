package eu.kanade.tachiyomi.extension.ja.drecomics

import eu.kanade.tachiyomi.lib.clipstudioreader.ClipStudioReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DreComics : ClipStudioReader() {
    override val name = "DRE Comics"
    override val baseUrl = "https://drecom-media.jp"
    override val lang = "ja"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/drecomics/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".seriesList__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                title = it.selectFirst(".seriesList__text")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("t", "2")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            var labelsAdded = false
            filters.forEach { filter ->
                when (filter) {
                    is LabelFilter -> filter.state.filter { it.state }.forEach {
                        addQueryParameter("l[]", it.value)
                        labelsAdded = true
                    }

                    is GenreFilter -> filter.state.filter { it.state }.forEach {
                        addQueryParameter("g[]", it.value)
                    }

                    else -> {}
                }
            }

            if (!labelsAdded) {
                addQueryParameter("l[]", "3") // DRE Comics (Manga)
                addQueryParameter("l[]", "1") // DRE Studios (Webtoon)
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.seriesColumn__list li.seriesColumn__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.seriesColumn__linkButton")!!.absUrl("href"))
                title = it.selectFirst("span.seriesColumn__title")!!.text()
                thumbnail_url = it.selectFirst("img.seriesColumn__img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a.pagination__link-next--active") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val isWebtoon = response.request.url.toString().contains("/drestudios")

        return SManga.create().apply {
            if (isWebtoon) {
                title = document.selectFirst(".detailStudios_title span")!!.text()
                author = document.select(".detailStudios_author p").eachText().joinToString()
                description = document.selectFirst(".detailStudios_storySynopsis")?.text()
                genre = document.select(".seriesDetail__genreLink--studios").eachText().joinToString()
                thumbnail_url = document.selectFirst(".detailStudios_mainLeft img.img-fluid")?.absUrl("src")
            } else {
                title = document.selectFirst(".detailComics_title span")!!.text()
                author = document.select(".detailComics_authorsItem").eachText().joinToString()
                description = document.selectFirst(".detailComics_synopsis")?.text()
                genre = document.select(".detailComics_genreListItem").eachText().joinToString()
                thumbnail_url = document.selectFirst(".detailComicsSection .img-fluid")?.absUrl("src")
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val isWebtoon = response.request.url.toString().contains("/drestudios")

        if (isWebtoon) {
            return document.select(".detailStudios_ebookList_item").map {
                SChapter.create().apply {
                    name = it.selectFirst(".detailStudios_ebookList_itemTitle")!!.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                }
            }.reversed()
        }

        return document.select("div.ebookListItem:not(.disabled) a.ebookListItem_title").map {
            SChapter.create().apply {
                name = it.selectFirst(".ebookListItem_title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                date_upload = dateFormat.tryParse(it.selectFirst(".ebookListItem_publishDate span")?.text()?.substringAfter("公開："))
            }
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        LabelFilter(getLabelList()),
        GenreFilter(getGenreList()),
    )

    private class Label(name: String, val value: String) : Filter.CheckBox(name)
    private class LabelFilter(labels: List<Label>) : Filter.Group<Label>("Label", labels)

    private class Genre(name: String, val value: String) : Filter.CheckBox(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    private fun getLabelList(): List<Label> = listOf(
        Label("DREコミックス（コミック）", "3"),
        Label("DRE STUDIOS（webtoon）", "1"),
    )

    private fun getGenreList(): List<Genre> = listOf(
        Genre("ファンタジー", "1"),
        Genre("バトル", "2"),
        Genre("恋愛", "3"),
        Genre("ラブコメ", "4"),
        Genre("SF", "5"),
        Genre("ミステリー", "6"),
        Genre("ホラー", "7"),
        Genre("シリアス", "34"),
        Genre("コメディ", "35"),
        Genre("業界", "57"),
        Genre("歴史", "53"),
        Genre("オカルト", "36"),
        Genre("アクション", "37"),
        Genre("もふもふ", "38"),
        Genre("サスペンス", "39"),
        Genre("異世界", "8"),
        Genre("異能", "9"),
        Genre("チート能力", "10"),
        Genre("タイムリープ", "40"),
        Genre("ゲーム世界", "12"),
        Genre("政治", "55"),
        Genre("経営", "56"),
        Genre("思想", "58"),
        Genre("ハーレム", "13"),
        Genre("聖女", "33"),
        Genre("追放", "15"),
        Genre("転生", "16"),
        Genre("転移", "17"),
        Genre("復讐", "18"),
        Genre("溺愛", "41"),
        Genre("悪役令嬢", "14"),
        Genre("王侯・貴族", "19"),
        Genre("婚約破棄", "42"),
        Genre("逆ハーレム", "43"),
        Genre("結婚", "44"),
        Genre("本格", "54"),
        Genre("日常", "21"),
        Genre("戦争", "45"),
        Genre("ドラマ", "30"),
        Genre("成り上がり", "47"),
        Genre("現代", "20"),
        Genre("学園", "22"),
        Genre("青春", "23"),
        Genre("お仕事", "24"),
        Genre("スローライフ", "25"),
        Genre("旅", "26"),
        Genre("子供", "27"),
        Genre("医療", "31"),
        Genre("グルメ", "28"),
        Genre("不良・ヤンキー", "29"),
        Genre("イケメン", "46"),
        Genre("メカ", "32"),
        Genre("魔王", "48"),
        Genre("人外", "49"),
        Genre("書き下ろし", "50"),
        Genre("エッセイ", "52"),
        Genre("コミカライズ", "51"),
        Genre("アニメ化", "62"),
        Genre("webtoon", "61"),
        Genre("受賞作", "63"),
        Genre("DREコミックス", "59"),
        Genre("DREコミックスF", "67"),
        Genre("完結", "65"),
    )

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
