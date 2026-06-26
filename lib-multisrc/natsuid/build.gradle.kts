plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    compileOnlyApi("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}

keiyoushi {
    baseVersionCode = 4
    libVersion = "1.4"
}
