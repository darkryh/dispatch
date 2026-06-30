package io.github.darkryh.dispatch.runtime

data class DispatchArgs(
    val rawArgs: List<String>,
    val flags: Set<String>,
    val arguments: Map<String, String>,
) {
    fun hasFlag(name: String): Boolean = flags.contains(name)

    fun getArgument(name: String): String? = arguments[name]

    fun requireArgument(name: String): String =
        arguments[name]
            ?: error(
                "Missing required argument '$name'. " +
                    "Provided: ${arguments.keys.sorted().joinToString(", ")}",
            )

    fun requireFlag(name: String) {
        if (!hasFlag(name)) {
            error("Missing required flag '--$name'.")
        }
    }
}

data class DispatchContext(
    val scope: DispatchScope,
    val args: DispatchArgs,
    val config: DispatchConfig,
)
