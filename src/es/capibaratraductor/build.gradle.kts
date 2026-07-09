plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CapibaraTraductor"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://capibaratraductor.com"
    }
}
