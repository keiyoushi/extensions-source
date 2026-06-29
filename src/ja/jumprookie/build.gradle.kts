plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jump Rookie!"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://rookie.shonenjump.com"
    }
}
