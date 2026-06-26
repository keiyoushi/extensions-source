plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Temple Scan"
    className = "TempleScan"
    versionCode = 49
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
