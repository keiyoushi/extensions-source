package eu.kanade.tachiyomi.extension.vi.umetruyen
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ

class UmeTruyen : ManhwaZ(
    "UmeTruyen",
    "https://umetruyenhay.com",
    "vi",
    mangaDetailsAuthorHeading = "Tác giả",
    mangaDetailsStatusHeading = "Trạng thái",
)
