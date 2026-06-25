plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mehgazone"
    className = "Mehgazone"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
