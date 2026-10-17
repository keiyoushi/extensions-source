import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadManga18"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimadara"

    source {
        lang = "en"
        baseUrl = "https://readmanga18.com"
    }

    deeplink {
        path("/manga/..*")
    }
}
