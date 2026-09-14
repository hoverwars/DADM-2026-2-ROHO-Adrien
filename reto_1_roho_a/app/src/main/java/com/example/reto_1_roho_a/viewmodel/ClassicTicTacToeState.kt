package com.example.reto_1_roho_a.viewmodel

import androidx.lifecycle.ViewModel
import com.example.reto_1_roho_a.data.ClassicTicTacToeBoard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClassicTicTacToeState : ViewModel()
{
    private val _uiState = MutableStateFlow(ClassicTicTacToeBoard());
    val uiState: StateFlow<ClassicTicTacToeBoard> = _uiState.asStateFlow();


}