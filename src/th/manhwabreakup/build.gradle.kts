plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaBreakup"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://www.manhwabreakup.com"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
