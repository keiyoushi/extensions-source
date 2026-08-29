import io.github.keiyoushi.gradle.api.ContentWarning

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
            baseUrl {
                mirrors(
                    "https://www.luscious.net",
                    "https://members.luscious.net",
                )
            }
        }
    }

    deeplink {
        host("www.luscious.net")
        host("members.luscious.net")
        path("/albums/..*")
    }
}
