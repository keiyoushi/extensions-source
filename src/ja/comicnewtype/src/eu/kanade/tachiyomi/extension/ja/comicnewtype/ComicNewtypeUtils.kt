package eu.kanade.tachiyomi.extension.ja.comicnewtype

fun String.halfwidthDigits() = buildString(length) {
    for (char in this@halfwidthDigits) {
        append(if (char in '０'..'９') char - ('０'.code - '0'.code) else char)
    }
}
