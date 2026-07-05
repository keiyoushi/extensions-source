plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luscious"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "ja", "es", "it", "de", "fr", "zh", "ko", "other", "th", "all", "pt-BR").forEach {
        source {
            lang = it
            if (it == "pt-BR") id = 5826725746643311801L
            baseUrl("https://www.luscious.net") {
                mirrors = listOf("https://members.luscious.net")
            }
        }
    }

    deeplink {
        host("www.luscious.net")
        host("members.luscious.net")
        path("/albums/..*")
    }
}
