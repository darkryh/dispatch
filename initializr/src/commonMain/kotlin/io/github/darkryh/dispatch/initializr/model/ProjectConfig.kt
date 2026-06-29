package io.github.darkryh.dispatch.initializr.model

/**
 * The user's choices, collected by the web form and fed to the generator.
 *
 * Everything here is a plain value: the generator turns a [ProjectConfig] into a list of
 * [io.github.darkryh.dispatch.initializr.template.TemplateFile]s by substituting these into the
 * placeholdered starter template. Keeping it free of any browser type is what lets the whole
 * generation pipeline be unit-tested on the JVM.
 */
data class ProjectConfig(
    val projectName: String,
    val groupId: String,
    val artifactId: String,
    val packageName: String,
    val appVersion: String,
) {
    /** The package as a directory path, e.g. `com.acme.app` -> `com/acme/app`. Used to place sources. */
    val packagePath: String get() = packageName.replace('.', '/')

    companion object {
        /** A sensible, immediately-valid starting point so the form is never empty on first load. */
        val DEFAULT =
            ProjectConfig(
                projectName = "My Dispatch App",
                groupId = "com.example",
                artifactId = "my-dispatch-app",
                packageName = "com.example.app",
                appVersion = "0.1.0",
            )
    }
}
