package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class TempleScan : HeanCms(
    "Temple Scan",
    "https://templescan.net",
    "en",
    apiUrl = "https://templescan.net/apiv1",
) {

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val mangaSubDirectory = "comic"

    override val enableLogin = true
}
