import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Slept Manga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://sleptmanga.com.tr"
    }
}
