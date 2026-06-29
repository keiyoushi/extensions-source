plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "One Punch Man Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://w11.1punchman.com"
    }
}
