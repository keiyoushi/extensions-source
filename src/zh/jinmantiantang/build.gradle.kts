plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jinman Tiantang"
    className = "Jinmantiantang"
    versionCode = 57
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
