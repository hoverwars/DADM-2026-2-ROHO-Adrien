package com.example.reto_7_roho_a.viewmodel

import android.app.Application
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_7_roho_a.R
import com.example.reto_7_roho_a.data.OnlineGame
import com.example.reto_7_roho_a.data.OnlineGameStatus
import com.example.reto_7_roho_a.data.HEARTBEAT_INTERVAL_MS
import com.example.reto_7_roho_a.data.OnlineGamesRepository
import com.example.reto_7_roho_a.data.OnlinePlayerPrefs
import com.example.reto_7_roho_a.data.Player
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "OnlineGameState"

/** État d'une partie en ligne : reflète en temps réel le document Firestore de la partie. */
class OnlineGameState(application: Application, private val gameId: String) :
    AndroidViewModel(application) {

    private val repository = OnlineGamesRepository()
    private val playerId = OnlinePlayerPrefs(application).playerId

    private val _game = MutableStateFlow<OnlineGame?>(null)
    private val _gameDeleted = MutableStateFlow(false)
    private val _connectionError = MutableStateFlow(false)
    private val _opponentLeft = MutableStateFlow(false)
    private var hasLeft = false

    val game: StateFlow<OnlineGame?> = _game.asStateFlow()
    /** Vrai si la partie n'existe plus côté serveur (supprimée par son créateur). */
    val gameDeleted: StateFlow<Boolean> = _gameDeleted.asStateFlow()
    val connectionError: StateFlow<Boolean> = _connectionError.asStateFlow()
    /** Vrai si l'adversaire a quitté la partie en cours : elle est fermée côté serveur. */
    val opponentLeft: StateFlow<Boolean> = _opponentLeft.asStateFlow()

    /** Le créateur est toujours X (bleu) ; l'invité est O (rouge). */
    val myMark: Player?
        get() = _game.value?.let { if (it.hostId == playerId) Player.Cross else if (it.guestId == playerId) Player.Circle else null }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val selectBlueSoundId = soundPool.load(application, R.raw.sound_select_blue, 1)
    private val selectRedSoundId = soundPool.load(application, R.raw.sound_select_red, 1)
    private val winSoundId = soundPool.load(application, R.raw.sound_win, 1)
    private val drawSoundId = soundPool.load(application, R.raw.sound_draw, 1)

    private val registration: ListenerRegistration = repository.listenGame(
        gameId,
        onChange = { updated ->
            _connectionError.value = false
            val previous = _game.value
            when {
                // Après notre départ, seule la suppression nous concerne (retour d'arrière-plan)
                hasLeft -> if (updated == null) _gameDeleted.value = true
                updated == null ->
                    // Supprimée après avoir eu deux joueurs : l'adversaire est parti
                    if (previous != null && previous.status != OnlineGameStatus.Waiting) _opponentLeft.value = true
                    else _gameDeleted.value = true
                updated.status == OnlineGameStatus.Abandoned -> {
                    _opponentLeft.value = true
                    // Le joueur resté ferme la partie : on la supprime du serveur
                    repository.delete(gameId)
                }
                else -> {
                    playSoundsFor(previous, updated)
                    _game.value = updated
                }
            }
        },
        onError = { _connectionError.value = true }
    )

    private fun playSoundsFor(previous: OnlineGame?, current: OnlineGame) {
        if (previous == null || previous.round != current.round) return
        val newMarks = current.board.indices.filter { previous.board[it] == null && current.board[it] != null }
        newMarks.lastOrNull()?.let { index ->
            val mark = current.board[index]
            soundPool.play(if (mark == Player.Cross) selectBlueSoundId else selectRedSoundId, 1f, 1f, 1, 0, 1f)
        }
        if (previous.status == OnlineGameStatus.Playing && current.status == OnlineGameStatus.Finished) {
            soundPool.play(if (current.isDraw) drawSoundId else winSoundId, 1f, 1f, 2, 0, 1f)
        }
    }

    fun isMyTurn(): Boolean {
        val game = _game.value ?: return false
        return game.status == OnlineGameStatus.Playing && game.turn == myMark
    }

    fun play(index: Int) {
        val mark = myMark ?: return
        // Seulement quand l'adversaire a rejoint et que c'est notre tour
        if (!isMyTurn()) {
            Log.d(TAG, "Movimiento ignorado: no es el turno o la partida no está en curso")
            return
        }
        if (_game.value?.board?.get(index) != null) return
        repository.play(gameId, index, mark) { ok ->
            if (!ok) Log.w(TAG, "La jugada en $index fue rechazada por el servidor")
        }
    }

    fun newGame() {
        if (_game.value?.status == OnlineGameStatus.Finished) repository.rematch(gameId)
    }

    init {
        // Tant que le créateur attend un rival, il signale que sa partie est toujours là
        viewModelScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val current = _game.value
                if (!hasLeft && current?.status == OnlineGameStatus.Waiting && current.hostId == playerId) {
                    repository.heartbeat(gameId)
                }
            }
        }
    }

    /** Quitte la partie : suppression si personne n'a rejoint, sinon abandon (l'adversaire est prévenu). */
    fun leave() {
        if (hasLeft) return
        hasLeft = true
        _game.value?.let { if (it.hostId == playerId || it.guestId == playerId) repository.leave(it) }
    }

    /** Appelé quand l'app passe en arrière-plan : une partie sans rival n'a plus lieu d'être proposée. */
    fun leaveIfWaiting() {
        if (_game.value?.status == OnlineGameStatus.Waiting) leave()
    }

    override fun onCleared() {
        registration.remove()
        leave()
        soundPool.release()
        super.onCleared()
    }
}
