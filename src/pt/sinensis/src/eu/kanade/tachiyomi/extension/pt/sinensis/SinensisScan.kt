package eu.kanade.tachiyomi.extension.pt.sinensis

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class SinensisScan : PeachScan(
    "Sinensis Scan",
    "https://sinensis.leitorweb.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy 'às' HH:mm", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
