plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHand"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "hentaihand"

    val languages = listOf(
        "all", "ja", "en", "zh", "bg", "ceb", "other", "tl", "ar", "el", "sr",
        "jv", "uk", "tr", "fi", "la", "mn", "eo", "sk", "cs", "ko", "ru", "it",
        "es", "pt-BR", "th", "fr", "id", "vi", "de", "pl", "hu", "nl", "hi",
    )

    languages.forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaihand.com"

            when (language) {
                "all" -> id = 1235047015955289468L
                "other" -> id = 7302549142935671434L
                "pt-BR" -> id = 2516244587139644000L
            }
        }
    }
}
