import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18.cc"
    versionCode = 65
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimadara"

    listOf("en", "ko", "all").forEach {
        source {
            lang = it
            baseUrl = "https://manhwa18.cc"
        }
    }

    deeplink {
        path("/webtoon/..*")
    }
}
