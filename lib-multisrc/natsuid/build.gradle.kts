import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 3

dependencies {
    compileOnlyApi("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
