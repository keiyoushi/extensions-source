plugins {
    id("lib-multisrc")
}

baseVersionCode = 47

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}
