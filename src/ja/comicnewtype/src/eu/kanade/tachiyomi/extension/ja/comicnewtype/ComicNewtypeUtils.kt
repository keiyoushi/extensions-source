package eu.kanade.tachiyomi.extension.ja.comicnewtype
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

fun String.halfwidthDigits() = buildString(length) {
    for (char in this@halfwidthDigits) {
        append(if (char in '０'..'９') char - ('０'.code - '0'.code) else char)
    }
}
