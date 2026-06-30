plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Natsu"
    versionCode = 32
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://natsu.tv"
    }
}
