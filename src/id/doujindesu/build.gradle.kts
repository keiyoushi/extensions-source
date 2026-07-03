plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujindesu"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl("https://doujin.desu.xxx") {
            withCustom = true
        }
        id = 7704282043609669342L
    }
}

dependencies {
    implementation(project(":lib:randomua"))
    implementation(project(":lib:cookieinterceptor"))
}
