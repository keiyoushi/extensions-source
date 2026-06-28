plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosmicScans.id"
    className = "CosmicScansID"
    versionCode = 55
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
