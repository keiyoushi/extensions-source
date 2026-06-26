plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nekopost"
    className = "Nekopost"
    versionCode = 15
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
