package eu.kanade.tachiyomi.extension.all.solarmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.machinetranslations.Language
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations

@RequiresApi(Build.VERSION_CODES.O)
class Solarmtl(
    language: Language,
) : MachineTranslations(
    name = "Solar Machine Translations",
    baseUrl = "https://solarmtl.com",
    language,
)
