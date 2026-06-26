plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lunar Manga"
    className = "LunarAnimeFactory"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
