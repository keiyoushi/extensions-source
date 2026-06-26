plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pash Up!"
    className = "PashUp"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:publus"))
}
