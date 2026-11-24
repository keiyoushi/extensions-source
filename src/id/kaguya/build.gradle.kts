import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kaguya"
    pkgName = "id.yubikiri"
    versionCode = 66
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "aesir"

    source {
        lang = "id"
        baseUrl = "https://01.kaguya.re"
        versionId = 2
    }
}
