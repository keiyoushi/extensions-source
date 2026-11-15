import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ahri Gallery"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf(
        "all", "ja", "en", "zh", "ko", "es", "ru", "pt-BR", "fr",
        "it", "th", "vi", "de", "pl", "hu", "nl",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://ahri-gallery.com"
        }
    }

    deeplink {
        path("/index.php")
    }
}
