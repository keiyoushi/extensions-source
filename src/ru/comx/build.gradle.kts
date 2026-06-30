plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Com-X"
    versionCode = 39
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        baseUrl("https://ru.com-x.life") {
            withCustom = true
        }
        lang = "ru"
        id = 1114173092141608635L
    }
}
