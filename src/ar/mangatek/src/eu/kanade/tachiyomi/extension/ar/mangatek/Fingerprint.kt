package eu.kanade.tachiyomi.extension.ar.mangatek

import android.app.ActivityManager
import android.content.res.Resources
import android.os.Build
import keiyoushi.utils.applicationContext
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonString
import okhttp3.Headers
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock

// The site reader's device fingerprint (fingerprint.<hash>.js), as a browser on this device reports it
class Fingerprint(sourceHeaders: Headers) {
    val fpc: Fpc
    val headers: Headers

    init {
        val userAgent = sourceHeaders["User-Agent"]!!
        val metrics = Resources.getSystem().displayMetrics
        // Shortest decimal, as JS prints it
        val dpr = metrics.density.toString().toDouble()
        val screen = "${(metrics.widthPixels / dpr).roundToInt()}x${(metrics.heightPixels / dpr).roundToInt()}"
        val mobile = "Android" in userAgent
        val platform = when {
            mobile -> "Linux armv8l"
            "Windows" in userAgent -> "Win32"
            "Macintosh" in userAgent -> "MacIntel"
            else -> "Linux x86_64"
        }
        val locale = Locale.getDefault()
        val lang = listOfNotNull(locale.toLanguageTag().takeIf { locale.country.isNotEmpty() }, locale.language).joinToString(",")
        val cores = Runtime.getRuntime().availableProcessors()
        val mem = deviceMemory()
        val touch = if (mobile) 5 else 0
        val timeZone = TimeZone.getDefault()
        val tzOffset = -timeZone.getOffset(Clock.System.now().toEpochMilliseconds()) / 60_000
        // 24-bit colour depth
        val screenWithDepth = "${screen}x24"

        // Stands in for the canvas render, which is the same for every device on one build
        val canvas = Build.FINGERPRINT

        val signals = Signals(
            ua = userAgent,
            lang = lang,
            platform = platform,
            cores = cores,
            mem = mem,
            touch = touch,
            tz = timeZone.id,
            tzOffset = tzOffset,
            screen = screenWithDepth,
            avail = screen,
            dpr = dpr,
            canvas = canvas,
            webgl = "",
        )
        fpc = Fpc(
            v = 1,
            ua = userAgent.take(300),
            lang = lang.take(60),
            platform = platform,
            cores = cores,
            mem = mem,
            touch = touch,
            tz = timeZone.id,
            tzOffset = tzOffset,
            screen = screenWithDepth,
            avail = screen,
            dpr = dpr,
            canvas = canvas.sha256().toHexString().take(16),
            webgl = "",
            app = FpcApp(
                webdriver = false,
                vendor = "Google Inc.",
                plugins = if (mobile) 0 else 5,
                pdfViewer = !mobile,
                chromeObj = "object",
                androidBridge = false,
                flutter = false,
                reactNative = false,
                webkitHandlers = false,
                standalone = false,
            ),
        )
        val device = listOf(
            platform,
            screenWithDepth,
            timeZone.id,
            "cores:$cores",
            "mem:$mem",
            "touch:$touch",
            "dpr:${dpr.toString().removeSuffix(".0")}",
        ).joinToString(" | ").replace(NON_ASCII_REGEX, "").take(256)

        headers = sourceHeaders.newBuilder()
            .add("x-fp", signals.toJsonString().sha256().toHexString())
            .add("x-device", device)
            .build()
    }

    // navigator.deviceMemory: the power of two nearest the RAM in GB, at most 8
    private fun deviceMemory(): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        applicationContext.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        val gigabytes = memoryInfo.totalMem / 1_073_741_824.0
        return listOf(1, 2, 4, 8).minBy { abs(it - gigabytes) }
    }

    companion object {
        private val NON_ASCII_REGEX = Regex("""[^\x20-\x7E]""")
    }
}
