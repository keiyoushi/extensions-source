plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Emperor Scan"
    className = "EmperorScan"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://imperiomanhua.com"
}

dependencies {

    implementation(project(":lib:randomua"))
}
