package com.ead.dispatch.sample.domain

import net.harawata.appdirs.AppDirsFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Pathing {
    val applicationDirectory : Path =  run {

        val path = Paths.get(
            AppDirsFactory.getInstance()
                .getUserDataDir("dispatch", null, "ead")
        )

        Files.createDirectories(path.toAbsolutePath())

        path
    }

}
