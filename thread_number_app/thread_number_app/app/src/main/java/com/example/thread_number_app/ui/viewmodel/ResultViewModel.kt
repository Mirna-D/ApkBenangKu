package com.example.thread_number_app.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

data class ResultUiState(
    val imagePath: String? = null,
    val top1: String? = null,
    val top2: String? = null
)

class ResultViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(ResultUiState())

    val uiState: StateFlow<ResultUiState> =
        _uiState.asStateFlow()

    fun setResult(
        imagePath: String,
        top1: String?,
        top2: String?
    ) {
        Log.d("VM_DEBUG", "SET RESULT CALLED")
        Log.d("VM_DEBUG", "ImagePath: $imagePath")
        Log.d("VM_DEBUG", "Top1: $top1")
        Log.d("VM_DEBUG", "Top2: $top2")

        _uiState.value =
            ResultUiState(
                imagePath = imagePath,
                top1 = top1,
                top2 = top2
            )
    }

    fun clear() {
        _uiState.value = ResultUiState()
    }
}