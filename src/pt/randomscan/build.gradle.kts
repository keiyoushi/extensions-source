plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lura Toon"
    className = "LuraToon"
    versionCode = 59
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    baseUrl = "https://luratoons.net"
}

dependencies {

    implementation(project(":lib:randomua"))
    implementation(project(":lib:zipinterceptor"))
}
