package eu.kanade.tachiyomi.extension.en.readcomiconline

import android.util.Base64
import java.net.URLDecoder

private fun step1(param: String): String {
    return param.substring(15, 15 + 18) + param.substring(15 + 18 + 17)
}

private fun step2(param: String): String {
    return param.substring(0, param.length - (9 + 2)) +
        param[param.length - 2] +
        param[param.length - 1]
}

fun decryptLink(
    firstStringFormat: String,
    formatter: String = "",
): String {
    // The replace for this can be handled via pref but for the sake of matching it with the site's
    // decryption logic, we'll leave it as it is
    var processedString = firstStringFormat
        .replace("pw_.g28x", "b")
        .replace("d2pr.x_27", "h")

    if (!processedString.startsWith("https")) {
        val firstStringFormatLocalVar = processedString
        val firstStringSubS = firstStringFormatLocalVar.substring(
            firstStringFormatLocalVar.indexOf("?"),
        )

        processedString = if (firstStringFormatLocalVar.contains("=s0?")) {
            firstStringFormatLocalVar.substring(0, firstStringFormatLocalVar.indexOf("=s0?"))
        } else {
            firstStringFormatLocalVar.substring(0, firstStringFormatLocalVar.indexOf("=s1600?"))
        }

        processedString = step1(processedString)
        processedString = step2(processedString)

        // Base64 decode and URL decode
        val decodedBytes = Base64.decode(processedString, Base64.DEFAULT)
        processedString = URLDecoder.decode(String(decodedBytes), "UTF-8")

        processedString = processedString.substring(0, 13) +
            processedString.substring(17)

        processedString = if (firstStringFormat.contains("=s0")) {
            processedString.substring(0, processedString.length - 2) + "=s0"
        } else {
            processedString.substring(0, processedString.length - 2) + "=s1600"
        }

        processedString += firstStringSubS
        processedString = "https://2.bp.blogspot.com/$processedString"
    }

    if (formatter.isNotEmpty()) {
        processedString = processedString.replace(
            "https://2.bp.blogspot.com",
            formatter,
        )
    }

    return processedString
}
