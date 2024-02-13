package eu.kanade.tachiyomi.extension.en.yaoitoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class YaoiToon : Madara("YaoiToon", "https://yaoitoon.com", "en", dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale.US))
