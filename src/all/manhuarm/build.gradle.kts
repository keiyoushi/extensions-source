plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    className = "Factory"
    versionCode = 25
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://manhuarmtl.com"
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
