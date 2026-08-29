import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hive Scans"
    pkgName = "en.infernalvoidscans"
    versionCode = 44
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "iken"

    source {
        baseUrl = "https://hivetoons.org"
        lang = "en"
        versionId = 2
    }
}
