plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Buon Dua"
    className = "BuonDua"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
