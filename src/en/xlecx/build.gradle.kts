import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XlecX"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://xlecx.one"
    }

    deeplink {
        path("/..*\\.html")
    }
}
