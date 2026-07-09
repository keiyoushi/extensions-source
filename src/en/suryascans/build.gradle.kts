plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Genz Toons"
    versionCode = 33
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://genztoons.org"
    }
}
