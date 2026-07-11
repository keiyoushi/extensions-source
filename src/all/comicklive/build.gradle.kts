plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comick (Unoriginal)"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf(
        "en", "ru", "vi", "fr", "pl", "id", "tr", "it", "es", "uk",
        "de", "ko", "th", "ro", "ms", "ja", "sv", "no",
    ).forEach {
        source {
            lang = it
            baseUrl {
                mirrors(
                    "https://comick.live",
                    "https://comick.art",
                )
            }
        }
    }
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
