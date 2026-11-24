import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "JenTranslation"
    theme = "zeistmanga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://jentranslation11.blogspot.com"
    }

    deeplink {
        path("/search/label/..*")
    }
}
