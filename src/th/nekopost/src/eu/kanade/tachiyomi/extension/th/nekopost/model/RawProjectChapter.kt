package eu.kanade.tachiyomi.extension.th.nekopost.model
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class RawProjectChapter(
    val chapterId: String?,
    val chapterNo: String,
    val chapterName: String,
    val status: String,
    val publishDate: String,
    val createDate: String,
    val providerName: String,
)
