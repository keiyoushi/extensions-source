plugins {
    id("lib-multisrc")
}

baseVersionCode = 2

dependencies {
    api(project(":lib:cookieinterceptor"))
}
