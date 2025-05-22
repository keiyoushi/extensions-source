package eu.kanade.tachiyomi.extension.pt.mediocretoons

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import keiyoushi.network.rateLimit

class MediocreToons : GreenShit(
    "Mediocre Toons",
    "https://mediocretoons.com",
    "pt-BR",
    scanId = 2,
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
