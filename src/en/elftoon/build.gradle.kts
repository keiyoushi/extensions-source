plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Elf Toon"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://elftoon.com"
    }
}
