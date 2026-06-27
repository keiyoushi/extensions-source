plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManHuaGui"
    className = "Manhuagui"
    versionCode = 28
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("manhuagui.com")
        host("m.manhuagui.com")
        host("www.manhuagui.com")
        host("tw.manhuagui.com")
        host("mhgui.com")
        host("m.mhgui.com")
        host("www.mhgui.com")
        host("tw.mhgui.com")
        path("/comic/..*")
    }
}

dependencies {

    implementation(project(":lib:lzstring"))
    implementation(project(":lib:unpacker"))
}
