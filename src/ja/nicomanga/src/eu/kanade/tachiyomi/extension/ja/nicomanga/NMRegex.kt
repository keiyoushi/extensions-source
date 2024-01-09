package eu.kanade.tachiyomi.extension.ja.nicomanga

object NMRegex {
    val thumbnailURLRegex: Regex = "background-image:[^;]url\\s*\\(\\s*'([^?']+)".toRegex()
    val statusRegex: Regex = "(?<=-)[^.]+".toRegex()
    val urlRegex: Regex = "(?<=manga-)[^/]+(?=\\.html\$)".toRegex()
    val floatRegex: Regex = "\\d+(?:\\.\\d+)?".toRegex()
    val chapterIdRegex: Regex = "(?<=imgsListchap\\()\\d+".toRegex()
}
