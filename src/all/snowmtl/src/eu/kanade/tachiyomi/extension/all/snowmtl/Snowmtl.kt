package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.machinetranslations.Language
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations

@RequiresApi(Build.VERSION_CODES.O)
class Snowmtl(
    language: Language,
) : MachineTranslations(
    name = "Snow Machine Translations",
    baseUrl = "https://snowmtl.ru",
    language,
)
