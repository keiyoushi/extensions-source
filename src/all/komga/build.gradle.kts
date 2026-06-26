plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komga"
    className = "KomgaFactory"
    versionCode = 65
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation("org.apache.commons:commons-text:1.11.0")
}
