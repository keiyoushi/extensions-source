plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoeTruyen"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://moetruyen.net")
            mirrorSpecial.add("https://moetruyen.net", "MoeTruyen.net (Trong nước)", "default")
            mirrorSpecial.add("https://truyen.moe", "Truyen.moe (Quốc tế)", "global")
        }
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
