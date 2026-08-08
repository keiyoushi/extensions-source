import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Softkomik"
    versionCode = 14
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            mirrors("https://softkomik.co", "https://softkomik.online")
        }
    }
}
