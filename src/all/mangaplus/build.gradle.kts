plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MANGA Plus by SHUEISHA"
    className = "MangaPlusFactory"
    versionCode = 62
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:i18n"))
}
