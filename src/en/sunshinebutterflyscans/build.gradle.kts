plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sunshine Butterfly Scans"
    className = "SunshineButterflyScans"
    versionCode = 39
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
