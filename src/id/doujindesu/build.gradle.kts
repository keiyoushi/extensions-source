import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujindesu"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl {
            custom("https://doujin.desu.xxx")
        }
        id = 7704282043609669342L
    }
}

dependencies {
    implementation(project(":lib:randomua"))
    implementation(project(":lib:cookieinterceptor"))
}
