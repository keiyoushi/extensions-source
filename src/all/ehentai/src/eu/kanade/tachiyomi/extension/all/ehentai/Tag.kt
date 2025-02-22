package eu.kanade.tachiyomi.extension.all.ehentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

/**
 * Simple tag model
 */
data class Tag(val name: String, val light: Boolean)
