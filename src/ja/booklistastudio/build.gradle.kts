import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Booklista Studio"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://studio.booklista.co.jp"
    }
}
