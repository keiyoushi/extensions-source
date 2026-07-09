plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yomu Mangás"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://yomumangas.com"
    }
}
