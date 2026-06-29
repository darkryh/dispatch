package io.github.darkryh.dispatch.initializr.template

/**
 * One file in the generated project: a repository-relative [path] and its full text [content].
 * Both the path and the content may contain `{{PLACEHOLDER}}` tokens that the generator substitutes
 * (the path matters because sources live under `src/main/kotlin/{{PACKAGE_PATH}}/`).
 */
data class TemplateFile(val path: String, val content: String)
