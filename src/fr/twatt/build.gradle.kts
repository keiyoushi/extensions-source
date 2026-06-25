plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twatt"
    className = "Twatt"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:dataimage"))
}
