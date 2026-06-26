plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comick (Unoriginal)"
    className = "ComickFactory"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
