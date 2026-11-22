package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * The reader's fingerprint token (m2).
 *
 * User agent, languages and platform follow the request's own headers. The hardware fields are picked once per
 * install from common values for that kind of browser, so installs differ without describing the real device.
 */
object M2 {

    fun fingerprint(userAgent: String, acceptLanguage: String?, timeZone: String, seed: Long): String {
        val random = Random(seed)
        val isChrome = "Chrome/" in userAgent
        val isMobile = "Android" in userAgent || "Mobile" in userAgent
        val (width, height) = (if (isMobile) MOBILE_SCREENS else DESKTOP_SCREENS).random(random)
        val browserBars = if (isMobile) random.nextInt(110, 150) else random.nextInt(80, 130)
        val fields = listOf(
            userAgent,
            languages(acceptLanguage),
            "x${width}y${height}c24p24",
            if (isChrome) (if (isMobile) MOBILE_MEMORY else DESKTOP_MEMORY).random(random) else "#",
            (if (isMobile) MOBILE_CORES else DESKTOP_CORES).random(random),
            if (isChrome) "Google Inc." else "#",
            platform(userAgent, random),
            "#",
            "#",
            timeZone,
            // Wide colour gamut: most phone screens, few desktop monitors.
            (random.nextInt(100) < if (isMobile) 80 else 20).toString(),
            "false",
            "#",
            "${width}x${height - browserBars}",
            isMobile.toString(),
            "#",
            "#",
            "2",
            "0",
            "0",
            "0",
        )
        return buildJsonObject {
            put("bilibili_0", 1)
            put("bilibili_16", "Error|at g|at encodeM2Sync|at Object.generate")
            put("bilibili_17", fields.joinToString("|"))
            put("bilibili_18", "C.de.1|C.ga.1|C.pa.1|C.ca.1|H.cm.1|H.tb.1|H.tL.1|H.tn.1|H.gt.1|O.tp.1|O.tb.1|O.cm.1|O.gt.1|O.de.1|O.ga.1|O.pa.1|O.ca.1|W.rs.1|W.gr.1|W.gn.1|W2.rs.1|W2.gr.1|W2.gn.1|U.cL.1|U.rL.1|w.Bb.1|H.ss.1|H.sg.1")
            put("bilibili_19", "r|v")
        }.toString()
    }

    // navigator.languages as the browser derives it from Accept-Language: the tags without their q-values.
    private fun languages(acceptLanguage: String?): String = acceptLanguage
        ?.split(',')
        ?.map { it.substringBefore(';').trim() }
        ?.filter { it.isNotEmpty() }
        ?.joinToString(",")
        ?.ifEmpty { null }
        ?: "en-US,en"

    // navigator.platform for the user agent's OS.
    private fun platform(userAgent: String, random: Random) = when {
        "Android" in userAgent -> listOf("Linux armv8l", "Linux aarch64").random(random)
        "Windows" in userAgent -> "Win32"
        "Macintosh" in userAgent -> "MacIntel"
        else -> "Linux x86_64"
    }

    // Base64 over the reader's own alphabet, with 'z' for padding ('=' is a data symbol there).
    fun fromBase64(base64: String): String = buildString {
        for (c in base64) append(if (c == '=') 'z' else M2_ALPHABET[BASE64_ALPHABET.indexOf(c)])
    }

    // Common sizes in CSS pixels.
    private val MOBILE_SCREENS = listOf(412 to 915, 393 to 873, 360 to 800, 384 to 854, 412 to 892, 360 to 780, 393 to 852, 384 to 832)
    private val DESKTOP_SCREENS = listOf(1920 to 1080, 1536 to 864, 1366 to 768, 2560 to 1440, 1440 to 900, 1280 to 720, 1680 to 1050)

    // navigator.deviceMemory, which Chrome caps at 8.
    private val MOBILE_MEMORY = listOf("4", "8", "8")
    private val DESKTOP_MEMORY = listOf("4", "8", "8", "8")
    private val MOBILE_CORES = listOf("8", "8", "8", "6")
    private val DESKTOP_CORES = listOf("4", "8", "8", "12", "16")

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val M2_ALPHABET = "ZBCDe0WGH1IJ9Kl3MNo2PQRstuVF+/=XY5Aab4cdEfghij6kLmnOpq7rSTU8vwxyz"
}
