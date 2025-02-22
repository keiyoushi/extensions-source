package eu.kanade.tachiyomi.multisrc.terrascan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val query: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)
