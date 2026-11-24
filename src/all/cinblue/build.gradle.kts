import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cin.blue"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "ja", "zh").forEach { language ->
        source {
            lang = language
            baseUrl = "https://cin.blue"
        }
    }

    deeplink {
        path("/v/..*")
        path("/g/..*")
    }
}
