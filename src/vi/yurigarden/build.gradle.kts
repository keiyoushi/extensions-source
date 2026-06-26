plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YuriGarden"
    className = "YuriGarden"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
