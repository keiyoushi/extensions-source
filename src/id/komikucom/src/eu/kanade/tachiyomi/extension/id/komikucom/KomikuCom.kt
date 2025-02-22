package eu.kanade.tachiyomi.extension.id.komikucom
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KomikuCom : MangaThemesia("Komiku.com", "https://komiku.one", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")))
