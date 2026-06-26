plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookWalker"
    className = "BookWalker"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:e4p"))
}
