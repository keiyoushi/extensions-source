plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    versionCode = 25
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    listOf("ar", "en", "es", "fr", "id", "it", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://manhuarmtl.com"
        }
    }
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
