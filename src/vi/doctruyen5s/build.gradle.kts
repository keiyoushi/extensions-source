import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DocTruyen5s"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "liliana"

    source {
        lang = "vi"
        baseUrl = "https://manga.io.vn"
    }
}
