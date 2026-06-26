plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MinoTruyen"
    className = "MinoTruyenFactory"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
