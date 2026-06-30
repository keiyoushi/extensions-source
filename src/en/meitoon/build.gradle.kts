plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeiToon"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://meitoon.org"
    }
}
