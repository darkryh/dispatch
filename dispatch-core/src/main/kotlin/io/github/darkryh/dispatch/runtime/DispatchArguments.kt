@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package io.github.darkryh.dispatch.runtime

internal data class ParsedArguments(
    val flags: Set<String>,
    val arguments: Map<String, String>,
)

internal fun parseDispatchArguments(
    args: Array<String>,
    config: DispatchConfig,
): ParsedArguments {
    val parsedFlags = mutableSetOf<String>()
    val parsedArguments = mutableMapOf<String, String>()

    var index = 0
    while (index < args.size) {
        index =
            parseArgumentAt(
                args = args,
                index = index,
                config = config,
                parsedFlags = parsedFlags,
                parsedArguments = parsedArguments,
            )
    }

    config.arguments.forEach { (name, def) ->
        if (name !in parsedArguments) {
            def.default?.let { parsedArguments[name] = it }
        }
    }

    return ParsedArguments(parsedFlags.toSet(), parsedArguments.toMap())
}

private fun parseArgumentAt(
    args: Array<String>,
    index: Int,
    config: DispatchConfig,
    parsedFlags: MutableSet<String>,
    parsedArguments: MutableMap<String, String>,
): Int {
    val arg = args[index]
    return when {
        arg.startsWith("--") ->
            parseLongArgument(
                name = arg.substring(2),
                args = args,
                index = index,
                config = config,
                parsedFlags = parsedFlags,
                parsedArguments = parsedArguments,
            ) + 1
        arg.startsWith("-") && arg.length > 2 ->
            parseLongArgument(
                name = arg.substring(1),
                args = args,
                index = index,
                config = config,
                parsedFlags = parsedFlags,
                parsedArguments = parsedArguments,
            ) + 1
        arg.startsWith("-") && arg.length == 2 ->
            parseShortArgument(
                shortName = arg[1],
                args = args,
                index = index,
                config = config,
                parsedFlags = parsedFlags,
                parsedArguments = parsedArguments,
            ) + 1
        else -> index + 1
    }
}

private fun parseShortArgument(
    shortName: Char,
    args: Array<String>,
    index: Int,
    config: DispatchConfig,
    parsedFlags: MutableSet<String>,
    parsedArguments: MutableMap<String, String>,
): Int {
    val flag = config.flags.values.find { it.shortName == shortName }
    if (flag != null) {
        parsedFlags.add(flag.name)
        return index
    }

    val argument = config.arguments.values.find { it.shortName == shortName }
    if (argument != null && index + 1 < args.size) {
        parsedArguments[argument.name] = args[index + 1]
        return index + 1
    }

    return index
}

private fun parseLongArgument(
    name: String,
    args: Array<String>,
    index: Int,
    config: DispatchConfig,
    parsedFlags: MutableSet<String>,
    parsedArguments: MutableMap<String, String>,
): Int {
    if (name.isBlank()) return index

    return when {
        config.flags.containsKey(name) -> {
            parsedFlags.add(name)
            index
        }
        config.arguments.containsKey(name) -> {
            if (index + 1 < args.size) {
                parsedArguments[name] = args[index + 1]
                index + 1
            } else {
                index
            }
        }
        name == "help" || name == "version" -> {
            parsedFlags.add(name)
            index
        }
        else -> {
            index
        }
    }
}
