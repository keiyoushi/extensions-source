plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosmicScans.id"
    className = "CosmicScansID"
    versionCode = 23
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://lc2.cosmicscans.to"
}

dependencies {

    implementation(project(":lib:randomua"))
}
