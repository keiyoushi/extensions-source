package eu.kanade.tachiyomi.extension.zh.dmzj

import eu.kanade.tachiyomi.extension.zh.dmzj.utils.RSA
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.serializer
import okhttp3.Response
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object ApiV4 {

    private const val v4apiUrl = "https://nnv4api.dmzj.com"

    fun mangaInfoUrl(id: String) = "$v4apiUrl/comic/detail/$id?uid=2665531"

    fun parseMangaInfo(response: Response): MangaDto? {
        val result: ResponseDto<MangaDto> = response.decrypt()
        return result.data
    }

    // path = "mangaId/chapterId"
    fun chapterImagesUrl(path: String) = "$v4apiUrl/comic/chapter/$path"

    fun parseChapterImages(response: Response, isLowRes: Boolean): ArrayList<Page> {
        val result: ResponseDto<ChapterImagesDto> = response.decrypt()
        return result.data!!.toPageList(isLowRes)
    }

    fun rankingUrl(page: Int, filters: RankingGroup) =
        "$v4apiUrl/comic/rank/list?${filters.parse()}&uid=2665531&page=$page"

    fun parseRanking(response: Response): MangasPage {
        val result: ResponseDto<List<RankingItemDto>> = response.decrypt()
        val data = result.data ?: return MangasPage(emptyList(), false)
        return MangasPage(data.map { it.toSManga() }, data.isNotEmpty())
    }

    private inline fun <reified T> Response.decrypt(): T = decrypt(typeOf<T>())

    @Suppress("UNCHECKED_CAST")
    private fun <T> Response.decrypt(type: KType): T {
        val bytes = RSA.decrypt(body.string(), cipher)
        val deserializer = serializer(type) as KSerializer<T>
        return ProtoBuf.decodeFromByteArray(deserializer, bytes)
    }

    @Serializable
    class MangaDto(
        @ProtoNumber(1) private val id: Int,
        @ProtoNumber(2) private val title: String,
        @ProtoNumber(6) private val cover: String,
        @ProtoNumber(7) private val description: String,
        @ProtoNumber(19) private val genres: List<TagDto>,
        @ProtoNumber(20) private val status: List<TagDto>,
        @ProtoNumber(21) private val authors: List<TagDto>,
        @ProtoNumber(23) private val chapterGroups: List<ChapterGroupDto>,
    ) {
        val isLicensed get() = chapterGroups.isEmpty()

        fun toSManga() = SManga.create().apply {
            url = getMangaUrl(id.toString())
            title = this@MangaDto.title
            author = authors.joinToString { it.name }
            description = if (isLicensed) {
                "${this@MangaDto.description}\n\n漫画 ID (1): $id"
            } else {
                this@MangaDto.description
            }
            genre = genres.joinToString { it.name }
            status = parseStatus(this@MangaDto.status[0].name)
            thumbnail_url = cover
            initialized = true
        }

        fun parseChapterList(): List<SChapter> {
            val mangaId = id.toString()
            val size = chapterGroups.sumOf { it.size }
            return chapterGroups.flatMapTo(ArrayList(size)) {
                it.toSChapterList(mangaId)
            }
        }
    }

    @Serializable
    class TagDto(@ProtoNumber(2) val name: String)

    @Serializable
    class ChapterGroupDto(
        @ProtoNumber(1) private val name: String,
        @ProtoNumber(2) private val chapters: List<ChapterDto>,
    ) {
        fun toSChapterList(mangaId: String): List<SChapter> {
            val groupName = name
            val isDefaultGroup = groupName == "连载"
            return chapters.map {
                it.toSChapterInternal().apply {
                    url = "$mangaId/$url"
                    if (!isDefaultGroup) name = "$groupName: $name"
                }
            }
        }

        val size get() = chapters.size
    }

    @Serializable
    class ChapterDto(
        @ProtoNumber(1) private val id: Int,
        @ProtoNumber(2) private val name: String,
        @ProtoNumber(3) private val updateTime: Long,
    ) {
        fun toSChapterInternal() = SChapter.create().apply {
            url = id.toString()
            name = this@ChapterDto.name.formatChapterName()
            date_upload = updateTime * 1000
        }
    }

    @Serializable
    class ChapterImagesDto(
        @ProtoNumber(6) private val lowResImages: List<String>,
        @ProtoNumber(8) private val images: List<String>,
    ) {
        fun toPageList(isLowRes: Boolean) =
            // page count can be messy, see manga ID 55847 chapters 107-109
            if (images.size == lowResImages.size) {
                parsePageList(images, lowResImages)
            } else if (isLowRes) {
                parsePageList(lowResImages, lowResImages)
            } else {
                parsePageList(images)
            }
    }

    // same as ApiV3.MangaDto
    @Serializable
    class RankingItemDto(
        @ProtoNumber(1) private val id: Int?,
        @ProtoNumber(2) private val title: String,
        @ProtoNumber(3) private val authors: String,
        @ProtoNumber(4) private val status: String,
        @ProtoNumber(5) private val cover: String,
        @ProtoNumber(6) private val genres: String,
        @ProtoNumber(9) private val slug: String?,
    ) {
        fun toSManga() = SManga.create().apply {
            url = when {
                id != null -> getMangaUrl(id.toString())
                slug != null -> PREFIX_ID_SEARCH + slug
                else -> throw Exception("无法解析")
            }
            title = this@RankingItemDto.title
            author = authors.formatList()
            genre = genres.formatList()
            status = parseStatus(this@RankingItemDto.status)
            thumbnail_url = cover
        }
    }

    @Serializable
    class ResponseDto<T>(
        @ProtoNumber(2) val message: String?,
        @ProtoNumber(3) val data: T?,
    )

    private val cipher by lazy { RSA.getPrivateKey("MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAK8nNR1lTnIfIes6oRWJNj3mB6OssDGx0uGMpgpbVCpf6+VwnuI2stmhZNoQcM417Iz7WqlPzbUmu9R4dEKmLGEEqOhOdVaeh9Xk2IPPjqIu5TbkLZRxkY3dJM1htbz57d/roesJLkZXqssfG5EJauNc+RcABTfLb4IiFjSMlTsnAgMBAAECgYEAiz/pi2hKOJKlvcTL4jpHJGjn8+lL3wZX+LeAHkXDoTjHa47g0knYYQteCbv+YwMeAGupBWiLy5RyyhXFoGNKbbnvftMYK56hH+iqxjtDLnjSDKWnhcB7089sNKaEM9Ilil6uxWMrMMBH9v2PLdYsqMBHqPutKu/SigeGPeiB7VECQQDizVlNv67go99QAIv2n/ga4e0wLizVuaNBXE88AdOnaZ0LOTeniVEqvPtgUk63zbjl0P/pzQzyjitwe6HoCAIpAkEAxbOtnCm1uKEp5HsNaXEJTwE7WQf7PrLD4+BpGtNKkgja6f6F4ld4QZ2TQ6qvsCizSGJrjOpNdjVGJ7bgYMcczwJBALvJWPLmDi7ToFfGTB0EsNHZVKE66kZ/8Stx+ezueke4S556XplqOflQBjbnj2PigwBN/0afT+QZUOBOjWzoDJkCQClzo+oDQMvGVs9GEajS/32mJ3hiWQZrWvEzgzYRqSf3XVcEe7PaXSd8z3y3lACeeACsShqQoc8wGlaHXIJOHTcCQQCZw5127ZGs8ZDTSrogrH73Kw/HvX55wGAeirKYcv28eauveCG7iyFR0PFB/P/EDZnyb+ifvyEFlucPUI0+Y87F") }
}
