plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LANraragi"
    versionCode = 23
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "LANraragi"
        lang = "all"
        baseUrl = "http://127.0.0.1:3000"
        skipCodeGen = true
    }
}
