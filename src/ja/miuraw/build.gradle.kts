import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiuRaw"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "ja.rawxz"

    source {
        lang = "ja"
        baseUrl = "https://miuraw.com"
        id = 7950551186567193810L
    }
}
