import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18.cc"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    listOf("en", "ko", "all").forEach {
        source {
            lang = it
            baseUrl = "https://manhwa18.cc"
        }
    }
}
