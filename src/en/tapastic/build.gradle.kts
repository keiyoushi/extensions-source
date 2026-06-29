plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tapas"
    className = "Tapastic"
    versionCode = 24
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
