plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FoxTruyen"
    className = "TruyenGG"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://foxtruyen2.com") {
            withCustom.set(true)
        }
        id = 1458993267006200127
    }
}
