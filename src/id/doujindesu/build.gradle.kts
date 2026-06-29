plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujindesu"
    className = "Doujindesu"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
    implementation(project(":lib:cookieinterceptor"))
}
