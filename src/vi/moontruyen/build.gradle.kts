plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoonTruyen"
    className = "MoonTruyen"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
