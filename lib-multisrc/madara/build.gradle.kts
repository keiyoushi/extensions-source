plugins {
    id("lib-multisrc")
}

baseVersionCode = 48

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}
