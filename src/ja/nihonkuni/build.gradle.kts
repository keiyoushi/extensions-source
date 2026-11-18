import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NihonKuni"
    pkgName = "ja.mangagun"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://nihonkuni.com"
        id = 3811800324362294701L
    }
}
