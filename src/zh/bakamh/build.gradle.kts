plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baka Manhua"
    className = "Bakamh"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://bakamh.com"
}

dependencies {

    implementation(project(":lib:randomua"))
}
