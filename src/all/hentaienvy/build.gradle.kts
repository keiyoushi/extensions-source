import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiEnvy"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "es", "fr", "ko", "de", "ru", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaienvy.com"
        }
    }
}
