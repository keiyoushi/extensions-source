plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    className = "KaganeFactory"
    versionCode = 26
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
