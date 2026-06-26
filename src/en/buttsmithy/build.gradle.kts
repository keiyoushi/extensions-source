plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "buttsmithy"
    className = "Buttsmithy"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
