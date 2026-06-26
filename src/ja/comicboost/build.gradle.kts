plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Boost"
    className = "ComicBoost"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:publus"))
}
