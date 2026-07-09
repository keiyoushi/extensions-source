plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Fury"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    val comicFuryUrl = "https://comicfury.com"

    listOf("all", "en", "es", "pt-BR", "de", "fr", "it", "pl", "ja", "zh", "ru", "fi", "other").forEach {
        source {
            lang = it
            baseUrl = comicFuryUrl
        }
    }
    source {
        name = "Comic Fury (No Text)"
        lang = "other"
        baseUrl = comicFuryUrl
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
