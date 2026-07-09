plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Elan School"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://elan.school"
    }
}
