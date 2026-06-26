plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webcomics"
    className = "Webcomics"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
