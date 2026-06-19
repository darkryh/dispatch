package com.ead.dispatch.sample.presentation.components

import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ComponentsViewModel : ViewModel() {
    private val mutableEnabled = MutableStateFlow(true)
    val enabledState = mutableEnabled.asStateFlow()

    private val mutableCount = MutableStateFlow(0)
    val count = mutableCount.asStateFlow()

    private val mutableName = MutableStateFlow("")
    val name = mutableName.asStateFlow()

    private val mutablePassword = MutableStateFlow("")
    val password = mutablePassword.asStateFlow()

    private val mutableCycleValue = MutableStateFlow("ALPHA")
    val cycleValue = mutableCycleValue.asStateFlow()

    private val mutableRadioIndex = MutableStateFlow(0)
    val radioIndex = mutableRadioIndex.asStateFlow()

    fun toggle() {
        mutableEnabled.update { !it }
    }

    fun increment() {
        mutableCount.update { it + 1 }
    }

    fun updateName(value: String) {
        mutableName.value = value
    }

    fun updatePassword(value: String) {
        mutablePassword.value = value
    }

    fun updateCycleValue(value: String) {
        mutableCycleValue.value = value
    }

    fun selectRadio(index: Int) {
        mutableRadioIndex.value = index
    }
}
