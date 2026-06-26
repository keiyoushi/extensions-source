plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaBug"
    className = "ManhuaBug"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://www.manhuabug.com"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
