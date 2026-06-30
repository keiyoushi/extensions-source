plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MG Komik"
    versionCode = 24
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://id.mgkomik.cc"
    }
}
