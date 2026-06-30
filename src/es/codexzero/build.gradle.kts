plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Codex Zero"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://codex.readkisho.me"
    }
}
