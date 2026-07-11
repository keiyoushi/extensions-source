import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fansubs.cat"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "fansubscat"

    source {
        lang = "ca"
        baseUrl = "https://manga.fansubs.cat"
    }
}
