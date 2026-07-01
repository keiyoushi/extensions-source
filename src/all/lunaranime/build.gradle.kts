plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lunar Manga"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    val languages = listOf(
        "all", "en", "ar", "bg", "bn", "da", "de", "es", "es-419",
        "fa", "fi", "fr", "he", "hi", "id", "it", "ja", "ko",
        "ms", "nl", "no", "pl", "pt", "pt-BR", "ru", "sv",
        "th", "tl", "tr", "ur", "vi", "zh",
    )

    languages.forEach { language ->
        source {
            baseUrl = "https://lunaranime.ru"
            lang = language
        }
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
