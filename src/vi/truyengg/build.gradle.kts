import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FoxTruyen"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://foxtruyen2.com")
        }
        id = 1458993267006200127
    }
}
