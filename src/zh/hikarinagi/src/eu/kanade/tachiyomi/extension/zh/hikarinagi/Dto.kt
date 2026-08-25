package eu.kanade.tachiyomi.extension.zh.hikarinagi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- /api/v3/mangas 列表 ----

@Serializable
class MangaListResponse(
    val success: Boolean = true,
    val data: MangaListData = MangaListData(),
)

@Serializable
class MangaListData(
    val items: List<MangaItem> = emptyList(),
)

@Serializable
class MangaItem(
    val id: Long,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String? = null,
    @SerialName("other_names") val otherNames: List<String>? = null,
    val covers: List<MangaCover> = emptyList(),
    val nsfw: Boolean = false,
    @SerialName("serial_status") val serialStatus: String? = null,
    @SerialName("publication_date") val publicationDate: String? = null,
    @SerialName("latest_chapter_at") val latestChapterAt: String? = null,
    val summary: String? = null,
    @SerialName("summary_cn") val summaryCn: String? = null,
    @SerialName("origin_country") val originCountry: String? = null,
    @SerialName("reading_mode") val readingMode: String? = null,
    @SerialName("publication_end_date") val publicationEndDate: String? = null,
)

@Serializable
class MangaCover(
    val media: Media? = null,
)

@Serializable
class Media(
    val id: Long = 0,
    val src: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

// ---- /api/v3/mangas/{id} 详情 ----

@Serializable
class MangaDetailResponse(
    val success: Boolean = true,
    val data: MangaItem? = null,
)

// ---- /api/v3/mangas/{id}/chapters ----

@Serializable
class ChaptersResponse(
    val success: Boolean = true,
    val data: List<ChapterItem> = emptyList(),
)

@Serializable
class ChapterItem(
    val id: Long,
    val chapterType: String? = null,
    @SerialName("chapter_number") val chapterNumber: String? = null,
    @SerialName("volume_number") val volumeNumber: String? = null,
    @SerialName("sort_key") val sortKey: Double? = null,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String? = null,
    @SerialName("publication_date") val publicationDate: String? = null,
    @SerialName("page_count") val pageCount: Int? = null,
    val cover: Media? = null,
    @SerialName("sources_count") val sourcesCount: Int? = null,
    val readable: Boolean = true,
)

// ---- /api/v3/mangas/{id}/tags ----

@Serializable
class TagsResponse(
    val success: Boolean = true,
    val data: List<TagItem> = emptyList(),
)

@Serializable
class TagItem(
    val likes: Long = 0,
    val tag: Tag? = null,
)

@Serializable
class Tag(
    val id: Long = 0,
    val name: String = "",
)

// ---- 详情页 SSR payload（Nuxt 3 __NUXT_DATA__）----

@Serializable
class DetailPayload(
    val manga: MangaItem? = null,
    val chapters: List<ChapterItem>? = null,
    val people: List<Staff>? = null,
    val producers: List<Staff>? = null,
    val tags: List<TagItem>? = null,
)

@Serializable
class Staff(
    val role: String? = null,
    val note: String? = null,
    val person: Person? = null,
    val producer: Person? = null,
)

@Serializable
class Person(
    val id: Long = 0,
    val name: String = "",
    @SerialName("trans_name") val transName: String? = null,
)

// ---- /api/pages/mangas/browse 列表/搜索 ----

@Serializable
class BrowseResponse(
    val list: BrowseList = BrowseList(),
    val state: BrowseState = BrowseState(),
)

@Serializable
class BrowseList(
    val items: List<BrowseItem> = emptyList(),
    val meta: BrowseMeta = BrowseMeta(),
)

@Serializable
class BrowseMeta(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 24,
    @SerialName("total_items") val totalItems: Long = 0,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
)

@Serializable
class BrowseItem(
    val id: Long,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String? = null,
    val covers: List<MangaCover> = emptyList(),
    val nsfw: Boolean = false,
    @SerialName("serial_status") val serialStatus: String? = null,
    @SerialName("publication_date") val publicationDate: String? = null,
    @SerialName("latest_chapter_at") val latestChapterAt: String? = null,
)

@Serializable
class BrowseState(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 24,
    val search: String? = null,
    @SerialName("sort_field") val sortField: String? = null,
    @SerialName("sort_order") val sortOrder: String? = null,
    val genre: List<String> = emptyList(),
)
