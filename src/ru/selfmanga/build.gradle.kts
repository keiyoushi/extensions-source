plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SelfManga"
    versionCode = 23
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl("https://1.selfmanga.live") {
            withCustom = true
        }
        lang = "ru"
        id = 5227602742162454547L
    }
}
