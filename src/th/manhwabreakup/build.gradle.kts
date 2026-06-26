plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaBreakup"
    className = "ManhwaBreakup"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://www.manhwabreakup.com"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
