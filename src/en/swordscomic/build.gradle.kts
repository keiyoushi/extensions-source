plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Swords Comic"
    className = "SwordsComic"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
