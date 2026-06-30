package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.Composable

@Composable
fun requireDispatchScope(): DispatchScope = LocalDispatchScope.current

@Composable
fun requireDispatchArgs(): DispatchArgs = LocalDispatchArgs.current

@Composable
fun requireDispatchContext(): DispatchContext = LocalDispatchContext.current

@Composable
fun requireArgument(name: String): String = requireDispatchArgs().requireArgument(name)

@Composable
fun requireFlag(name: String) = requireDispatchArgs().requireFlag(name)
