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
        baseUrl("https://moetruyen.net") {
            entries.add("MoeTruyen.net (Trong nước)")
            values.add("default")

            mirrorSpecial.add("https://truyen.moe", "Truyen.moe (Quốc tế)", "global")
        }
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
