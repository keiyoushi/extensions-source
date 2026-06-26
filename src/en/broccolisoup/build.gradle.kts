plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Broccoli Soup"
    className = "BroccoliSoup"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
