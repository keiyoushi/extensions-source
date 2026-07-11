plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf(
        "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr", "he", "hi", "hu",
        "id", "it", "is", "ja", "ko", "kn", "ml", "ms", "ne", "nl", "no", "pl", "pt-BR", "ro", "ru", "sk",
        "sl", "sq", "sr", "sv", "ta", "th", "tr", "uk", "vi", "zh",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangaball.net"
        }
    }

    deeplink {
        host("mangaball.net")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
