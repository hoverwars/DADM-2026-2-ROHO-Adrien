package com.example.reto_7_roho_a.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.reto_7_roho_a.data.OnlineGame
import com.example.reto_7_roho_a.data.OnlineGamesRepository
import com.example.reto_7_roho_a.data.OnlinePlayerPrefs
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "OnlineLobbyState"

class OnlineLobbyState(application: Application) : AndroidViewModel(application) {
    private val repository = OnlineGamesRepository()
    private val playerPrefs = OnlinePlayerPrefs(application)

    private val _games = MutableStateFlow<List<OnlineGame>>(emptyList())
    private val _loading = MutableStateFlow(true)
    private val _connectionError = MutableStateFlow(false)
    private val _busy = MutableStateFlow(false)
    private val _playerName = MutableStateFlow(playerPrefs.playerName)

    val games: StateFlow<List<OnlineGame>> = _games.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val connectionError: StateFlow<Boolean> = _connectionError.asStateFlow()
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val playerName: StateFlow<String> = _playerName.asStateFlow()
    val playerId: String get() = playerPrefs.playerId

    private val registration: ListenerRegistration = repository.listenAvailableGames(
        onChange = { list ->
            Log.d(TAG, "Partidas disponibles: ${list.size}")
            _games.value = list
            _loading.value = false
            _connectionError.value = false
        },
        onError = {
            _loading.value = false
            _connectionError.value = true
        }
    )

    fun setPlayerName(name: String) {
        _playerName.value = name.take(com.example.reto_7_roho_a.data.MAX_PLAYER_NAME_LENGTH)
    }

    private fun effectiveName(): String =
        _playerName.value.trim().ifEmpty { "Jugador" }.also { playerPrefs.playerName = it }

    fun createGame(onResult: (String?) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        repository.createGame(playerId, effectiveName()) { id ->
            _busy.value = false
            onResult(id)
        }
    }

    fun joinGame(gameId: String, onResult: (Boolean) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        repository.joinGame(gameId, playerId, effectiveName()) { ok ->
            _busy.value = false
            onResult(ok)
        }
    }

    override fun onCleared() {
        registration.remove()
        super.onCleared()
    }
}
