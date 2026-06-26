plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gaugau Monster Plus"
    className = "GaugauMonsterPlus"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:speedbinb"))
}
