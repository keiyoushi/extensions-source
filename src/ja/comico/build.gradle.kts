plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comico"
    className = "Comico"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
