plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosmicScans.id"
    className = "CosmicScansID"
    versionCode = 22
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://lc1.cosmicscans.to"
}

dependencies {

    implementation(project(":lib:randomua"))
}
