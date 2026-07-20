import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "3Hentai"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf(
        "all", "en", "ja", "ko", "zh", "mo", "es", "pt", "id", "jv",
        "tl", "vi", "th", "my", "tr", "ru", "uk", "pl", "fi", "de",
        "it", "fr", "nl", "cs", "hu", "bg", "is", "la", "ar",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://3hentai.net"
            // lang changed from po to pl, id kept from before the rename
            if (it == "pl") id = 7940950215101782907L
        }
    }

    deeplink {
        path("/d/..*")
    }
}
