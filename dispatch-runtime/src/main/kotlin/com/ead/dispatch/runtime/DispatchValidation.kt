package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.Dispatchable

@Dispatchable
fun requireDispatchScope(): DispatchScope = LocalDispatchScope.current

@Dispatchable
fun requireDispatchArgs(): DispatchArgs = LocalDispatchArgs.current

@Dispatchable
fun requireDispatchContext(): DispatchContext = LocalDispatchContext.current

@Dispatchable
fun requireArgument(name: String): String = requireDispatchArgs().requireArgument(name)

@Dispatchable
fun requireFlag(name: String) = requireDispatchArgs().requireFlag(name)
