package eu.kanade.tachiyomi.extension.all.snowmtl.translator.google
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import okhttp3.Response

class GoogleTranslatorDto

data class Translated(
    val from: String,
    val to: String,
    val origin: String,
    val text: String,
    val pronunciation: String,
    val extraData: Map<String, Any?>,
    val response: Response,
)
