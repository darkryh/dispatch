plugins {
    id("dispatch.kotlin-library-conventions")
    id("dispatch.kotlin-serialization")
}

dependencies {
    api(projects.dispatchUpdate)
}
