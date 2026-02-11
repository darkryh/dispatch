package com.ead.dispatch.runtime.util

import java.nio.file.Path
import kotlin.io.path.pathString

fun Path.toCliPathString(): String = "~${this.pathString.removePrefix("/")}"
