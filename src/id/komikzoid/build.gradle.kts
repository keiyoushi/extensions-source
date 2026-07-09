plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komikzoid"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "colorlibanime"

    source {
        lang = "id"
        baseUrl = "https://komikzoid.id"
    }
}
