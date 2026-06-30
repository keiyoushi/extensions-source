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
        baseUrl("https://foxtruyen2.com") {
            withCustom = true
        }
        id = 1458993267006200127
    }
}
