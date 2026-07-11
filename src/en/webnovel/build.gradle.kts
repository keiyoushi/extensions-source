import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebNovel"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.webnovel.com"
        id = 4081135203808920563L
    }
}
