plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VIZ"
    className = "VizFactory"
    versionCode = 25
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation("com.drewnoakes:metadata-extractor:2.18.0")
}
