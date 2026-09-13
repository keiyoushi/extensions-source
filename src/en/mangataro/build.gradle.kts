import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTaro"
    pkgName = "all.mangataro"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "mangataro"

    source {
        lang = "en"
        baseUrl = "https://mangataro.org"
    }
}
