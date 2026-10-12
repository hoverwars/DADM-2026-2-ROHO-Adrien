package com.example.reto_7_roho_a.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private const val TAG = "OnlineGamesRepository"
private const val COLLECTION = "games"

// Le créateur d'une partie en attente envoie un signe de vie régulier ; sans nouvelles depuis
// WAITING_GAME_MAX_SILENCE_MS (app tuée sans nettoyage), la partie n'est plus proposée.
const val HEARTBEAT_INTERVAL_MS = 15_000L
private const val WAITING_GAME_MAX_SILENCE_MS = 45_000L

/** Accès au backend Firebase (Cloud Firestore) pour les parties en ligne. */
class OnlineGamesRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val games get() = db.collection(COLLECTION)

    /** Écoute en temps réel la liste des parties disponibles (en attente d'un second joueur). */
    fun listenAvailableGames(
        onChange: (List<OnlineGame>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        games.whereEqualTo("status", OnlineGameStatus.Waiting.value)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error al escuchar las partidas disponibles", error)
                    onError(error)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val list = snapshot?.documents.orEmpty()
                    .mapNotNull { OnlineGame.fromSnapshot(it) }
                    .filter { now - it.updatedAtMillis < WAITING_GAME_MAX_SILENCE_MS }
                    .sortedByDescending { it.createdAtMillis }
                onChange(list)
            }

    fun createGame(hostId: String, hostName: String, onResult: (String?) -> Unit) {
        val doc = games.document()
        val data = hashMapOf<String, Any?>(
            "hostId" to hostId,
            "hostName" to hostName,
            "guestId" to null,
            "guestName" to null,
            "status" to OnlineGameStatus.Waiting.value,
            "board" to OnlineGame.EMPTY_BOARD,
            "turn" to "X",
            "winner" to "",
            "scoreX" to 0,
            "scoreO" to 0,
            "round" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        doc.set(data)
            .addOnSuccessListener {
                Log.i(TAG, "Partida creada: ${doc.id}")
                onResult(doc.id)
            }
            .addOnFailureListener {
                Log.e(TAG, "No se pudo crear la partida", it)
                onResult(null)
            }
    }

    /** Rejoint une partie : transaction pour qu'un seul invité puisse rejoindre. */
    fun joinGame(gameId: String, guestId: String, guestName: String, onResult: (Boolean) -> Unit) {
        val ref = games.document(gameId)
        db.runTransaction { tx ->
            val game = OnlineGame.fromSnapshot(tx.get(ref))
                ?: throw IllegalStateException("La partida ya no existe")
            if (game.status != OnlineGameStatus.Waiting || game.guestId != null) {
                throw IllegalStateException("La partida ya no está disponible")
            }
            tx.update(ref, mapOf(
                "guestId" to guestId,
                "guestName" to guestName,
                "status" to OnlineGameStatus.Playing.value,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            null
        }.addOnSuccessListener {
            Log.i(TAG, "Unido a la partida $gameId")
            onResult(true)
        }.addOnFailureListener {
            Log.w(TAG, "No se pudo unir a la partida $gameId", it)
            onResult(false)
        }
    }

    fun listenGame(
        gameId: String,
        onChange: (OnlineGame?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        games.document(gameId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error al escuchar la partida $gameId", error)
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.let { OnlineGame.fromSnapshot(it) })
        }

    /** Joue un coup. Valide tour/case/statut dans la transaction, puis calcule victoire/nul. */
    fun play(gameId: String, index: Int, mark: Player, onResult: (Boolean) -> Unit) {
        val ref = games.document(gameId)
        db.runTransaction { tx ->
            val game = OnlineGame.fromSnapshot(tx.get(ref))
                ?: throw IllegalStateException("La partida ya no existe")
            if (game.status != OnlineGameStatus.Playing) throw IllegalStateException("La partida no está en curso")
            if (game.turn != mark) throw IllegalStateException("No es tu turno")
            if (game.board[index] != null) throw IllegalStateException("Casilla ocupada")

            val board = game.board.toMutableList().apply { this[index] = mark }
            val winner = OnlineGame.winnerOf(board)
            val draw = winner == null && board.none { it == null }
            val finished = winner != null || draw
            val winnerCode = if (draw) "D" else if (winner != null) OnlineGame.encodePlayer(winner) else ""

            tx.update(ref, mapOf(
                "board" to OnlineGame.encodeBoard(board),
                "turn" to OnlineGame.encodePlayer(if (mark == Player.Cross) Player.Circle else Player.Cross),
                "status" to (if (finished) OnlineGameStatus.Finished else OnlineGameStatus.Playing).value,
                "winner" to winnerCode,
                "scoreX" to game.scoreX + if (winner == Player.Cross) 1 else 0,
                "scoreO" to game.scoreO + if (winner == Player.Circle) 1 else 0,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            null
        }.addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                Log.w(TAG, "Jugada rechazada en $gameId: ${it.message}")
                onResult(false)
            }
    }

    /** Nouvelle manche une fois la précédente terminée (le premier des deux joueurs qui appuie). */
    fun rematch(gameId: String) {
        val ref = games.document(gameId)
        db.runTransaction { tx ->
            val game = OnlineGame.fromSnapshot(tx.get(ref))
            if (game != null && game.status == OnlineGameStatus.Finished) {
                val nextRound = game.round + 1
                tx.update(ref, mapOf(
                    "board" to OnlineGame.EMPTY_BOARD,
                    "turn" to OnlineGame.encodePlayer(OnlineGame.starterOfRound(nextRound)),
                    "status" to OnlineGameStatus.Playing.value,
                    "winner" to "",
                    "round" to nextRound,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            null
        }.addOnFailureListener { Log.w(TAG, "Nueva partida rechazada en $gameId", it) }
    }

    /** Signe de vie du créateur pour que sa partie en attente reste visible dans le lobby. */
    fun heartbeat(gameId: String) {
        games.document(gameId).update("updatedAt", FieldValue.serverTimestamp())
            .addOnFailureListener { Log.d(TAG, "Heartbeat ignorado en $gameId: ${it.message}") }
    }

    /** Supprime une partie abandonnée (par le joueur resté, une fois informé du départ). */
    fun delete(gameId: String) {
        games.document(gameId).delete()
            .addOnFailureListener { Log.d(TAG, "No se pudo borrar la partida $gameId: ${it.message}") }
    }

    /** Quitte la partie : supprime si personne n'a rejoint, sinon la marque abandonnée. */
    fun leave(game: OnlineGame) {
        val ref = games.document(game.id)
        val task = when (game.status) {
            OnlineGameStatus.Waiting, OnlineGameStatus.Abandoned -> ref.delete()
            OnlineGameStatus.Playing, OnlineGameStatus.Finished -> ref.update(mapOf(
                "status" to OnlineGameStatus.Abandoned.value,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }
        task.addOnFailureListener { Log.w(TAG, "No se pudo abandonar la partida ${game.id}", it) }
    }
}
