package eu.kanade.tachiyomi.extension.zh.dmzj

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Response
import org.jsoup.parser.Parser

object ApiV3 {

    private const val v3apiUrl = "https://v3api.idmzj.com"
    private const val apiUrl = "https://api.dmzj.com"

    fun popularMangaUrl(page: Int) = "$v3apiUrl/classify/0/0/${page - 1}.json"

    fun latestUpdatesUrl(page: Int) = "$v3apiUrl/classify/0/1/${page - 1}.json"

    fun pageUrl(page: Int, filters: FilterList) = "$v3apiUrl/classify/${parseFilters(filters)}/${page - 1}.json"

    fun parsePage(response: Response): MangasPage {
        val data: List<MangaDto> = response.parseAs()
        return MangasPage(data.map { it.toSManga() }, data.isNotEmpty())
    }

    fun mangaInfoUrlV1(id: String) = "$apiUrl/dynamic/comicinfo/$id.json"

    private fun parseMangaInfoV1(response: Response): ResponseDto = try {
        response.parseAs()
    } catch (_: Throwable) {
        throw Exception("获取漫画信息失败")
    }

    fun parseMangaDetailsV1(response: Response): SManga {
        return parseMangaInfoV1(response).data.info.toSManga()
    }

    fun parseChapterListV1(response: Response): List<SChapter> {
        val data = parseMangaInfoV1(response).data
        return buildList(data.list.size + data.alone.size) {
            data.list.mapTo(this) {
                it.toSChapter()
            }
            data.alone.mapTo(this) {
                it.toSChapter().apply { name = "单行本: $name" }
            }
        }
    }

    fun chapterImagesUrlV1(path: String) = "https://m.idmzj.com/chapinfo/$path.html"

    fun parseChapterImagesV1(response: Response) =
        response.parseAs<ChapterImagesDto>().toPageList()

    fun chapterCommentsUrl(path: String) = "$v3apiUrl/viewPoint/0/$path.json"

    fun parseChapterComments(response: Response, count: Int): List<String> {
        val result: List<ChapterCommentDto> = response.parseAs()
        if (result.isEmpty()) return listOf("没有吐槽")
        val aggregated = result.groupBy({ it.content }, { it.num }).map { (content, likes) ->
            ChapterCommentDto(Parser.unescapeEntities(content, false), likes.sum())
        } as ArrayList
        aggregated.sort()
        return aggregated.take(count).map { it.toString() }
    }

    @Serializable
    class MangaDto(
        private val id: JsonPrimitive, // can be int or string
        private val title: String,
        private val authors: String?,
        private val status: String,
        private val cover: String,
        private val types: String,
        private val description: String? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            url = getMangaUrl(id.content)
            title = this@MangaDto.title
            author = authors?.formatList()
            genre = types.formatList()
            status = parseStatus(this@MangaDto.status)
            thumbnail_url = cover

            val desc = this@MangaDto.description ?: return@apply
            description = "$desc\n\n漫画 ID (2): ${id.content}" // hidden
            initialized = true
        }
    }

    @Serializable
    class ChapterDto(
        private val id: String,
        private val comic_id: String,
        private val chapter_name: String,
        private val updatetime: String,
    ) {
        fun toSChapter() = SChapter.create().apply {
            url = "$comic_id/$id"
            name = chapter_name.formatChapterName()
            date_upload = updatetime.toLong() * 1000
        }
    }

    @Serializable
    class ChapterImagesDto(
        private val page_url: List<String>,
    ) {
        fun toPageList() = parsePageList(page_url)
    }

    @Serializable
    class ChapterCommentDto(
        val content: String,
        val num: Int,
    ) : Comparable<ChapterCommentDto> {
        override fun toString() = if (num > 0) "$content [+$num]" else content
        override fun compareTo(other: ChapterCommentDto) = other.num.compareTo(num) // descending
    }

    @Serializable
    class DataDto(val info: MangaDto, val list: List<ChapterDto>, val alone: List<ChapterDto>)

    @Serializable
    class ResponseDto(val data: DataDto)
}
