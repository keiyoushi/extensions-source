import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "IMHentai"
    versionCode = 15
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "es", "fr", "ko", "de", "ru", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://imhentai.xxx"
        }
    }
}
