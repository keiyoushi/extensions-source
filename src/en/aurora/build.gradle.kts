import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "aurora"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Aurora"
        lang = "en"
        baseUrl = "https://comicaurora.com"
    }
}
