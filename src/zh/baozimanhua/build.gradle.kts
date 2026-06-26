plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baozi Manhua"
    className = "Baozi"
    versionCode = 28
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation("com.github.stevenyomi:baozibanner:9ac9b08e1d") // 1.0
}
