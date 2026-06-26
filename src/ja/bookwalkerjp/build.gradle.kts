plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookWalker Japan"
    className = "BookWalkerJp"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
}

dependencies {
    implementation(project(":lib:publus"))
    implementation(project(":lib:cookieinterceptor"))
}
