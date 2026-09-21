package eu.kanade.tachiyomi.extension.th.nekopost

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EditorProject(
    @SerialName("pid")
    val pid: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("projectType")
    val projectType: String,
    @SerialName("status")
    val status: Int,
    @SerialName("aliasName")
    val aliasName: String? = null,
    @SerialName("website")
    val website: String? = null,
    @SerialName("authorName")
    val authorName: String? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("coverVersion")
    val coverVersion: Int? = null,
)

@Serializable
class PagingInfo(
    val pageNo: Int,
    val pageSize: Int,
)

@Serializable
class ProjectRequestBody(
    @SerialName("pid")
    val pid: Int,
)

@Serializable
class RawChapterInfo(
    @SerialName("chapterId")
    val chapterId: Int,
    @SerialName("pageItem")
    val pageItem: List<RawPageItem>,
    @SerialName("projectId")
    val projectId: String,
)

@Serializable
class RawLatestChapterList(
    @SerialName("listChapter")
    val listChapter: List<RawLatestChapter>? = null,
)

@Serializable
class RawLatestChapter(
    @SerialName("pid")
    val pid: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("coverVersion")
    val coverVersion: Int,
    @SerialName("status")
    val status: String,
)

@Serializable
class RawPageItem(
    @SerialName("pageName")
    val pageName: String? = null,
    @SerialName("fileName")
    val fileName: String? = null,
    @SerialName("pageNo")
    val pageNo: Int,
)

@Serializable
data class RawProject(
    @SerialName("pid")
    val projectId: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("aliasName")
    val aliasName: String,
    @SerialName("website")
    val website: String,
    @SerialName("authorName")
    val authorName: String,
    @SerialName("artistName")
    val artistName: String,
    @SerialName("info")
    val info: String,
    @SerialName("status")
    val status: Int,
    @SerialName("flgMature")
    val flgMature: String,
    @SerialName("releaseDate")
    val releaseDate: RawValidString,
)

@Serializable
class RawProjectCategory(
    @SerialName("CateName")
    val categoryName: String,
)

@Serializable
class RawProjectChapter(
    @SerialName("ChapterID")
    val chapterId: Int,
    @SerialName("ChapterNo")
    val chapterNo: String,
    @SerialName("ChapterName")
    val chapterName: String,
    @SerialName("PublishDate")
    val publishDate: RawValidString,
    @SerialName("ProviderName")
    val providerName: String,
)

@Serializable
class RawProjectInfo(
    @SerialName("projectInfo")
    val info: RawProjectInfoData?,
)

@Serializable
class RawProjectInfoData(
    @SerialName("Project")
    val project: RawProject,
    @SerialName("ListCate")
    val category: List<RawProjectCategory>?,
    @SerialName("ListChapter")
    val chapter: List<RawProjectChapter>?,
)

@Serializable
class RawProjectSearchSummary(
    @SerialName("pid")
    val pid: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("status")
    val status: Int,
    @SerialName("projectType")
    val projectType: String,
    @SerialName("coverVersion")
    val coverVersion: Int,
)

@Serializable
class RawProjectSearchSummaryList(
    val listProject: List<RawProjectSearchSummary>? = null,
)

@Serializable
class RawValidString(
    @SerialName("String")
    val value: String,
)

@Serializable
class SearchRequest(
    val keyword: String,
    val status: Int,
    val paging: PagingInfo,
)

@Serializable
class UpdatesRequest(
    val type: String,
    val paging: PagingInfo,
)
