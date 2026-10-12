package com.example.reto_7_roho_a.data

import com.google.firebase.firestore.DocumentSnapshot

enum class OnlineGameStatus(val value: String) {
    Waiting("waiting"),
    Playing("playing"),
    Finished("finished"),
    Abandoned("abandoned");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value } ?: Abandoned
    }
}

// Partie en ligne telle que stockée dans Firestore (collection "games").
// Le créateur joue toujours X (bleu), l'invité joue O (rouge).
data class OnlineGame(
    val id: String,
    val hostId: String,
    val hostName: String,
    val guestId: String?,
    val guestName: String?,
    val status: OnlineGameStatus,
    val board: List<Player?>,
    val turn: Player,
    val winner: Player?,
    val isDraw: Boolean,
    val scoreX: Int,
    val scoreO: Int,
    val round: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    companion object {
        const val EMPTY_BOARD = "---------"

        fun fromSnapshot(doc: DocumentSnapshot): OnlineGame? {
            val data = doc.data ?: return null
            val winnerCode = data["winner"] as? String ?: ""
            return OnlineGame(
                id = doc.id,
                hostId = data["hostId"] as? String ?: return null,
                hostName = data["hostName"] as? String ?: "Jugador",
                guestId = data["guestId"] as? String,
                guestName = data["guestName"] as? String,
                status = OnlineGameStatus.from(data["status"] as? String),
                board = decodeBoard(data["board"] as? String ?: EMPTY_BOARD),
                turn = decodePlayer(data["turn"] as? String) ?: Player.Cross,
                winner = decodePlayer(winnerCode),
                isDraw = winnerCode == "D",
                scoreX = (data["scoreX"] as? Number)?.toInt() ?: 0,
                scoreO = (data["scoreO"] as? Number)?.toInt() ?: 0,
                round = (data["round"] as? Number)?.toInt() ?: 0,
                // Null brièvement pour les écritures locales avant que le serveur ne confirme
                createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
            )
        }

        fun encodeBoard(board: List<Player?>): String =
            board.joinToString("") { encodePlayer(it) }

        fun decodeBoard(value: String): List<Player?> =
            List(9) { decodePlayer(value.getOrNull(it)?.toString()) }

        fun encodePlayer(player: Player?): String = when (player) {
            Player.Cross -> "X"
            Player.Circle -> "O"
            null -> "-"
        }

        fun decodePlayer(value: String?): Player? = when (value) {
            "X" -> Player.Cross
            "O" -> Player.Circle
            else -> null
        }

        // Le joueur qui commence alterne à chaque nouvelle manche
        fun starterOfRound(round: Int): Player = if (round % 2 == 0) Player.Cross else Player.Circle

        private val LINES = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )

        fun winnerOf(board: List<Player?>): Player? =
            LINES.firstOrNull { (a, b, c) -> board[a] != null && board[a] == board[b] && board[b] == board[c] }
                ?.let { board[it[0]] }
    }
}
