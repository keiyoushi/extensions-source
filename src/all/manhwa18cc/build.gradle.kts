import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18.cc"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "ko", "all").forEach {
        source {
            lang = it
            baseUrl = "https://manhwa18.cc"
        }
    }
}
