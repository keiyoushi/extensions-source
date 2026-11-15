package eu.kanade.tachiyomi.extension.ja.comicfesta

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class RankingResponse(
    val titles: List<RankingTitle>,
)

@Serializable
class RankingTitle(
    private val id: Int,
    private val name: String,
    private val thumbnailPath: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnailPath
    }
}

@Serializable
class ChapterListResponse(
    private val packages: List<ChapterPackage>,
    private val userPackages: List<UserPackage>?,
    private val userStatus: String?,
) {
    private val isLoggedIn get() = userStatus != null && userStatus != "guest"

    fun toSChapterList(hideLocked: Boolean): List<SChapter> {
        val ownedIds = userPackages.orEmpty().map { it.id }.toSet()
        return packages
            .filter { !hideLocked || !it.isLocked(isLoggedIn, ownedIds) }
            .map { it.toSChapter(isLoggedIn, ownedIds) }
    }
}

@Serializable
class UserPackage(
    val id: Int,
)

@Serializable
class ChapterPackage(
    private val id: Int,
    private val number: Float,
    private val name: String?,
    private val point: Int?,
    private val fairInfo: FairInfo?,
) {
    private fun downloadPath(loggedIn: Boolean, owned: Boolean): String = when {
        owned -> "download"
        fairInfo?.free?.endAt != null -> "free_download"
        fairInfo?.trial?.endAt != null -> "trial_download"
        point == 0 && !loggedIn -> "free_download"
        else -> "download"
    }

    fun isLocked(loggedIn: Boolean, ownedIds: Set<Int>): Boolean {
        val owned = id in ownedIds
        return downloadPath(loggedIn, owned) == "download" && !owned
    }

    fun toSChapter(loggedIn: Boolean, ownedIds: Set<Int>) = SChapter.create().apply {
        val owned = id in ownedIds
        val lock = if (isLocked(loggedIn, ownedIds)) "🔒 " else ""
        val path = downloadPath(loggedIn, owned)
        val num = number.toString().removeSuffix(".0")
        url = "$id/$path"
        name = lock + (this@ChapterPackage.name?.takeIf { it.isNotBlank() } ?: "${num}巻")
        chapter_number = number
    }
}

@Serializable
class FairInfo(
    val free: Readable?,
    val trial: Readable?,
)

@Serializable
class Readable(
    val endAt: String?,
)
