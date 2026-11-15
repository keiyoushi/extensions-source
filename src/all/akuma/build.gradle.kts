import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Akuma"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf(
        "all", "en", "id", "jv", "ca", "ceb", "cs", "da", "de", "et", "es", "eo",
        "fr", "it", "hi", "hu", "nl", "pl", "pt", "vi", "tr", "ru", "uk", "ar",
        "ko", "zh", "ja",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://akuma.moe"
        }
    }

    deeplink {
        host("akuma.moe")
        path("/g/..*")
    }
}
