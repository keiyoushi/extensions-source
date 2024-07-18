package eu.kanade.tachiyomi.multisrc.libgroup

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class Constants(
    @SerialName("ageRestriction") val ageRestrictions: List<IdLabelSiteType>,
    @SerialName("format") val formats: List<IdNameSiteType>,
    val genres: List<IdNameSiteType>,
    val imageServers: List<ImageServer>,
    @SerialName("scanlateStatus") val scanlateStatuses: List<IdLabelSiteType>,
    @SerialName("status") val titleStatuses: List<IdLabelSiteType>,
    val tags: List<IdNameSiteType>,
    val types: List<IdLabelSiteType>,
) {
    @Serializable
    class IdLabelSiteType(
        val id: Int,
        val label: String,
        @SerialName("site_ids") val siteIds: List<Int>,
    )

    @Serializable
    class IdNameSiteType(
        val id: Int,
        val name: String,
        @SerialName("site_ids") val siteIds: List<Int>,
    )

    @Serializable
    class ImageServer(
        val id: String,
        val label: String,
        val url: String,
        @SerialName("site_ids") val siteIds: List<Int>,
    )

    fun getServer(isServers: String?, siteId: Int): ImageServer =
        if (!isServers.isNullOrBlank()) {
            imageServers.first { it.id == isServers && it.siteIds.contains(siteId) }
        } else {
            imageServers.first { it.siteIds.contains(siteId) }
        }

    fun getCategories(siteId: Int): List<IdLabelSiteType> = types.filter { it.siteIds.contains(siteId) }
    fun getFormats(siteId: Int): List<IdNameSiteType> = formats.filter { it.siteIds.contains(siteId) }
    fun getGenres(siteId: Int): List<IdNameSiteType> = genres.filter { it.siteIds.contains(siteId) }
    fun getTags(siteId: Int): List<IdNameSiteType> = tags.filter { it.siteIds.contains(siteId) }
    fun getScanlateStatuses(siteId: Int): List<IdLabelSiteType> = scanlateStatuses.filter { it.siteIds.contains(siteId) }
    fun getTitleStatuses(siteId: Int): List<IdLabelSiteType> = titleStatuses.filter { it.siteIds.contains(siteId) }
    fun getAgeRestrictions(siteId: Int): List<IdLabelSiteType> = ageRestrictions.filter { it.siteIds.contains(siteId) }
}

@Serializable
class MangasPageDto(
    val data: List<MangaShort>,
    val meta: MangaPageMeta,
) {
    @Serializable
    class MangaPageMeta(
        @SerialName("has_next_page") val hasNextPage: Boolean,
    )

    fun mapToSManga(isEng: String): List<SManga> {
        return this.data.map { it.toSManga(isEng) }
    }
}

@Serializable
class MangaShort(
    val name: String,
    @SerialName("rus_name") val rusName: String?,
    @SerialName("eng_name") val engName: String?,
    @SerialName("slug_url") val slugUrl: String,
    val cover: Cover,
) {
    @Serializable
    data class Cover(
        val default: String?,
    )

    fun toSManga(isEng: String) = SManga.create().apply {
        title = getSelectedLanguage(isEng, rusName, engName, name)
        thumbnail_url = cover.default.orEmpty()
        url = "/$slugUrl"
    }
}

@Serializable
class Manga(
    val type: LabelType,
    val ageRestriction: LabelType,
    val rating: Rating,
    val genres: List<NameType>,
    val tags: List<NameType>,
    @SerialName("rus_name") val rusName: String?,
    @SerialName("eng_name") val engName: String?,
    val name: String,
    val cover: MangaShort.Cover,
    val authors: List<NameType>,
    val artists: List<NameType>,
    val status: LabelType,
    val scanlateStatus: LabelType,
    @SerialName("is_licensed") val isLicensed: Boolean,
    val otherNames: List<String>,
    val summary: String,
) {
    @Serializable
    class LabelType(
        val label: String,
    )

    @Serializable
    class NameType(
        val name: String,
    )

    @Serializable
    class Rating(
        val average: Float,
        val votes: Int,
    )

    fun toSManga(isEng: String): SManga = SManga.create().apply {
        title = getSelectedLanguage(isEng, rusName, engName, name)
        thumbnail_url = cover.default
        author = authors.joinToString { it.name }
        artist = artists.joinToString { it.name }
        status = parseStatus(isLicensed, scanlateStatus.label, this@Manga.status.label)
        genre = type.label.ifBlank { "Манга" } + ", " + ageRestriction.label + ", " +
            genres.joinToString { it.name.trim() } + ", " + tags.joinToString { it.name.trim() }
        description = getOppositeLanguage(isEng, rusName, engName) + rating.average.parseAverage() + " " + rating.average +
            " (голосов: " + rating.votes + ")\n" + otherNames.joinAltNames() + summary
    }

    private fun Float.parseAverage(): String {
        return when {
            this > 9.5 -> "★★★★★"
            this > 8.5 -> "★★★★✬"
            this > 7.5 -> "★★★★☆"
            this > 6.5 -> "★★★✬☆"
            this > 5.5 -> "★★★☆☆"
            this > 4.5 -> "★★✬☆☆"
            this > 3.5 -> "★★☆☆☆"
            this > 2.5 -> "★✬☆☆☆"
            this > 1.5 -> "★☆☆☆☆"
            this > 0.5 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }
    }

    private fun parseStatus(isLicensed: Boolean, statusTranslate: String, statusTitle: String): Int = when {
        isLicensed -> SManga.LICENSED
        statusTranslate == "Завершён" && statusTitle == "Приостановлен" || statusTranslate == "Заморожен" || statusTranslate == "Заброшен" -> SManga.ON_HIATUS
        statusTranslate == "Завершён" && statusTitle == "Выпуск прекращён" -> SManga.CANCELLED
        statusTranslate == "Продолжается" -> SManga.ONGOING
        statusTranslate == "Выходит" -> SManga.ONGOING
        statusTranslate == "Завершён" -> SManga.COMPLETED
        statusTranslate == "Вышло" -> SManga.PUBLISHING_FINISHED
        else -> when (statusTitle) {
            "Онгоинг" -> SManga.ONGOING
            "Анонс" -> SManga.ONGOING
            "Завершён" -> SManga.COMPLETED
            "Приостановлен" -> SManga.ON_HIATUS
            "Выпуск прекращён" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun List<String>.joinAltNames(): String = when {
        this.isNotEmpty() -> "Альтернативные названия:\n" + this.joinToString(" / ") + "\n\n"
        else -> ""
    }
}

private fun getSelectedLanguage(isEng: String, rusName: String?, engName: String?, name: String): String = when {
    isEng == "rus" && rusName.orEmpty().isNotEmpty() -> rusName!!
    isEng == "eng" && engName.orEmpty().isNotEmpty() -> engName!!
    else -> name
}

private fun getOppositeLanguage(isEng: String, rusName: String?, engName: String?): String = when {
    isEng == "eng" && rusName.orEmpty().isNotEmpty() -> rusName + "\n"
    isEng == "rus" && engName.orEmpty().isNotEmpty() -> engName + "\n"
    else -> ""
}

@Serializable
class Chapter(
    val id: Int,
    @SerialName("branches_count") val branchesCount: Int,
    val branches: List<Branch>,
    val name: String?,
    val number: String,
    val volume: String,
) {
    @Serializable
    class Branch(
        @SerialName("branch_id") val branchId: Int?,
        @SerialName("created_at") val createdAt: String,
        val teams: List<Team>,
        val user: User,
    ) {
        @Serializable
        class Team(
            val name: String,
        )

        @Serializable
        class User(
            val username: String,
        )
    }

    private fun first(branchId: Int? = null): Branch? {
        return runCatching { if (branchId != null) branches.first { it.branchId == branchId } else branches.first() }.getOrNull()
    }

    private fun getTeamName(branchId: Int? = null): String? {
        return runCatching { first(branchId)!!.teams.first().name }.getOrNull()
    }

    private fun getUserName(branchId: Int? = null): String? {
        return runCatching { first(branchId)!!.user.username }.getOrNull()
    }

    fun toSChapter(slugUrl: String, branchId: Int? = null, isScanUser: Boolean): SChapter = SChapter.create().apply {
        val chapterName = "Том $volume. Глава $number"
        name = if (this@Chapter.name.isNullOrBlank()) chapterName else "$chapterName - ${this@Chapter.name}"
        val branchStr = if (branchId != null) "&branch_id=$branchId" else ""
        url = "/$slugUrl/chapter?$branchStr&volume=$volume&number=$number"
        scanlator = getTeamName(branchId) ?: if (isScanUser) getUserName(branchId) else null
        date_upload = runCatching { LibGroup.simpleDateFormat.parse(first(branchId)!!.createdAt)!!.time }.getOrDefault(0L)
        chapter_number = number.toFloat()
    }
}

fun List<Chapter>.getBranchCount(): Int = this.maxOf { chapter -> chapter.branches.size }

@Serializable
class Branch(
    val id: Int,
)

@Serializable
class Pages(
    val pages: List<MangaPage>,
) {
    @Serializable
    class MangaPage(
        val slug: Int,
        val url: String,
    )

    fun toPageList(): List<Page> = pages.map { Page(it.slug, it.url) }
}

@Serializable
class AuthToken(
    private val auth: Auth?,
    private val token: Token?,
) {
    @Serializable
    class Auth(
        val id: Int,
    )

    @Serializable
    class Token(
        val timestamp: Long,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("token_type") val tokenType: String,
        @SerialName("access_token") val accessToken: String,
    )

    fun isValid(): Boolean = auth != null && token != null

    fun isExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expiresIn = token!!.timestamp + (token.expiresIn * 1000)
        return expiresIn < currentTime
    }

    fun getToken(): String = "${token!!.tokenType} ${token.accessToken}"

    fun getUserId(): Int = auth!!.id
}
