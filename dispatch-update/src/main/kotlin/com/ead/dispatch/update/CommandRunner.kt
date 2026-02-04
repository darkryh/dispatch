package com.ead.dispatch.update

import java.io.IOException

interface CommandRunner {
    fun run(vararg args: String): CommandResult
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

class SystemCommandRunner : CommandRunner {
    override fun run(vararg args: String): CommandResult {
        return try {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            CommandResult(exitCode, stdout, stderr)
        } catch (exception: IOException) {
            CommandResult(-1, "", exception.message.orEmpty())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult(-1, "", exception.message.orEmpty())
        }
    }
}
