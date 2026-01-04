package com.ead.dispatch.runtime

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

    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg.startsWith("--") -> {
                i = parseLongArgument(
                    name = arg.substring(2),
                    args = args,
                    index = i,
                    config = config,
                    parsedFlags = parsedFlags,
                    parsedArguments = parsedArguments,
                )
            }
            arg.startsWith("-") && arg.length > 2 -> {
                i = parseLongArgument(
                    name = arg.substring(1),
                    args = args,
                    index = i,
                    config = config,
                    parsedFlags = parsedFlags,
                    parsedArguments = parsedArguments,
                )
            }
            arg.startsWith("-") && arg.length == 2 -> {
                val shortName = arg[1]
                val flag = config.flags.values.find { it.shortName == shortName }
                if (flag != null) {
                    parsedFlags.add(flag.name)
                } else {
                    val argument = config.arguments.values.find { it.shortName == shortName }
                    if (argument != null && i + 1 < args.size) {
                        parsedArguments[argument.name] = args[++i]
                    }
                }
            }
        }
        i++
    }

    config.arguments.forEach { (name, def) ->
        if (name !in parsedArguments) {
            def.default?.let { parsedArguments[name] = it }
        }
    }

    return ParsedArguments(parsedFlags.toSet(), parsedArguments.toMap())
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
        else -> index
    }
}
