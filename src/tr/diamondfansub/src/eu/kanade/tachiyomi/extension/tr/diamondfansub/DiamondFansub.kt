package eu.kanade.tachiyomi.extension.tr.diamondfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DiamondFansub : Madara("DiamondFansub", "https://diamondfansub.com", "tr", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("tr")))
