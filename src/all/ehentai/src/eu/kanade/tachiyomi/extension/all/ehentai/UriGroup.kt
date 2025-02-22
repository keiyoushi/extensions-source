package eu.kanade.tachiyomi.extension.all.ehentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import android.net.Uri
import eu.kanade.tachiyomi.source.model.Filter

/**
 * UriGroup
 */
open class UriGroup<V>(name: String, state: List<V>) : Filter.Group<V>(name, state), UriFilter {
    override fun addToUri(builder: Uri.Builder) {
        state.forEach {
            if (it is UriFilter) it.addToUri(builder)
        }
    }
}
