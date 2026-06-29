plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic CX"
    className = "ComicCX"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:dataimage"))
}
