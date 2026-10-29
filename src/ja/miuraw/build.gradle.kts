import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiuRaw"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "ja.rawxz"

    source {
        lang = "ja"
        baseUrl {
            custom("https://miuraw.net")
        }
        id = 7950551186567193810L
    }
}
