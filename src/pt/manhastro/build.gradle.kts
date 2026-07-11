plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhastro"
    versionCode = 58
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://manhastro.net"
    }
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
