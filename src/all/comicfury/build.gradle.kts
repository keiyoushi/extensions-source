plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Fury"
    className = "ComicFuryFactory"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
