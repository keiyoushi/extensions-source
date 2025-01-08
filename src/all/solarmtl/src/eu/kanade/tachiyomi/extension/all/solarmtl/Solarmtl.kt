package eu.kanade.tachiyomi.extension.all.solarmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.machinetranslations.Language
import eu.kanade.tachiyomi.multisrc.machinetranslations.MachineTranslations
import eu.kanade.tachiyomi.network.interceptor.rateLimit

@RequiresApi(Build.VERSION_CODES.O)
class Solarmtl(
    language: Language,
) : MachineTranslations(
    name = "Solar Machine Translations",
    baseUrl = "https://solarmtl.com",
    language,
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
