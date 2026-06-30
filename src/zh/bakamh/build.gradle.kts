plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baka Manhua"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "巴卡漫画"
        lang = "zh"
        baseUrl("https://bakamh.com") {
            withCustom = true
        }
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
