import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NHentai.xxx"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "zh", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://nhentai.xxx"
        }
    }
}
