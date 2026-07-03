plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Vinne Veritas - CCC"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            name = "Vinnie Veritas - CCC"
            lang = it
            baseUrl = "https://ccc.vinnieveritas.com"
        }
    }
}
