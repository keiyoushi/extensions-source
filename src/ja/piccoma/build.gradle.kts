plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Piccoma"
    className = "Piccoma"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:seedrandom"))
}
