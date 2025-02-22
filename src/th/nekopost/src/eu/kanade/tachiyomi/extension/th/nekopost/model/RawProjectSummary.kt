package eu.kanade.tachiyomi.extension.th.nekopost.model
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class RawProjectSummary(
    val cover: String,
    val chapterId: String,
    val chapterName: String,
    val chapterNo: String,
    val createDate: String,
    val providerName: String,
    val noNewChapter: String,
    val projectName: String,
    val projectId: String,
)
