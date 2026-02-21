package eu.kanade.tachiyomi.extension.en.mistminthaven

data class NovelDetailRes(
    val data: NovelDetail,
)

data class NovelDetail(
    val id: String,
    val title: String,
    val altTitle: String?,
    val slug: String,
    val description: String,
    val author: String,
    val illustrator: String?,
    val avatarUrl: String,
    val nativeLanguage: String,
    val isHidden: Boolean,
    val isMature: Boolean,
    val status: Int,
    val views: Int,
    val bookmarksCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val lastReleasedAt: String?,
    val novelNote: String?,
    val novelUpdateURL: String?,
    val createdBy: CreatedBy,
    val genres: List<Genre>,
)

data class ChapterListRes(
    val data: List<Volume>,
)
data class Volume(
    val chapters: List<Chapter>,
    val volumeIndex: Int,
    val volumeTitle: String,
)
data class Chapter(
    val id: String,
    val title: String,
    val slug: String,
    val chapterNumber: String,
    val price: Int,
    val isFree: Boolean,
    val freeAt: String,
    val isHidden: Boolean,
    val createdAt: String,
    val order: Int,
    val isPurchased: Boolean,
    val isOwner: Boolean,
)
data class Html2CanvasReq(
    val qSelector: String,
    val url: String,
    val scripts: String?,
    val fallbackImage: String,
    val style: Html2CanvasReqStyle?,
)
data class Html2CanvasReqStyle(
    val hide: List<String>,
    val fontSize: String,
    val addMarginTopTo: String,
    val marginTop: String,
)
