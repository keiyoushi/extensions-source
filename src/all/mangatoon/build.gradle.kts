plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaToon (Limited)"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("zh", "en", "id", "vi", "es", "th", "fr", "ja", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://mangatoon.mobi"
            if (it == "pt-BR") id = 2064722193112934135
        }
    }
}
