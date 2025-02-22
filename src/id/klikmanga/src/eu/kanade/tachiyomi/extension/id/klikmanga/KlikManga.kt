package eu.kanade.tachiyomi.extension.id.klikmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class KlikManga : Madara(
    "KlikManga",
    "https://klikmanga.com",
    "id",
    SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val mangaSubString = "daftar-komik"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
