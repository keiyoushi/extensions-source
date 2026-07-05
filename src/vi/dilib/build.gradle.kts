plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dilib"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://dilib.vn") {
            withCustom = true
        }
    }
}
