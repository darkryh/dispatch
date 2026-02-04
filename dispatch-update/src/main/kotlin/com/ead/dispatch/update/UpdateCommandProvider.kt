package com.ead.dispatch.update

interface UpdateCommandProvider {
    fun commandFor(source: UpdateSource, packageName: String): String?
}

class DefaultUpdateCommandProvider : UpdateCommandProvider {
    override fun commandFor(source: UpdateSource, packageName: String): String? {
        return when (source) {
            UpdateSource.HOMEBREW -> "brew update && brew upgrade $packageName"
            UpdateSource.SCOOP -> "scoop update && scoop update $packageName"
            UpdateSource.APT -> "sudo apt update && sudo apt install --only-upgrade $packageName"
            UpdateSource.GITHUB -> null
            UpdateSource.MANUAL -> null
            UpdateSource.UNKNOWN -> null
        }
    }
}
