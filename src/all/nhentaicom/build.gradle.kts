import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "nHentai.com (unoriginal)"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "hentaihand"

    val languages = listOf(
        "all", "zh", "en", "ja", "other", "ar", "jv", "bg", "cs",
        "uk", "sk", "eo", "mn", "la", "ceb", "tl", "fi", "tr",
        "sr", "el", "ko", "ro",
    )

    languages.forEach { language ->
        source {
            lang = language
            baseUrl = "https://nhentai.com"

            when (language) {
                "all" -> id = 9165839893600661480L
                "en" -> id = 5591830863732393712L
                "other" -> id = 5817327335315373850L
                "cs" -> id = 1144495813995437124L
            }
        }
    }
}
