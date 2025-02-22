package eu.kanade.tachiyomi.extension.ru.mangaonelove
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOneLove : Madara("Manga One Love", "https://mangaonelove.site", "ru", SimpleDateFormat("dd.MM.yyyy", Locale.US))
