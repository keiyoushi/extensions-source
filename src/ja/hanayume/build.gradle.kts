import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hana To Yume+"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://hanayume.com"
    }
}
