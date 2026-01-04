package com.ead.dispatch.sample.domain

import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider

object Storage {
    val provider = JVMFilePersistenceStorageProvider(Pathing.applicationDirectory.toAbsolutePath())
}