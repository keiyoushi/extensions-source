plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TuttoAnimeManga"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://tuttoanimemanga.net"
    }
}
