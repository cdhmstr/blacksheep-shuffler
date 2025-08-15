package com.ghancaballes.blacksheep

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.LinkedList
import kotlin.math.abs

class PlayerManagementActivity : AppCompatActivity() {

    // Penalty weights for team formation
    private companion object {
        const val PARTNER_REPEAT_PENALTY = 10.0
        const val OPPONENT_REPEAT_PENALTY = 5.0
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var playersCollection: CollectionReference

    // --- UI Elements ---
    private lateinit var titleTextView: TextView

    // Setup Views
    private lateinit var setupContainer: LinearLayout
    private lateinit var playersRecyclerView: RecyclerView
    private lateinit var courtCountEditText: EditText
    private lateinit var startSessionButton: Button

    // Game Views
    private lateinit var restingPlayersTextView: TextView
    private lateinit var courtsRecyclerView: RecyclerView
    private lateinit var gameActionButtons: LinearLayout
    private lateinit var addLatePlayerButton: Button
    private lateinit var addCourtButton: Button
    private lateinit var endSessionButton: Button

    // --- Adapters ---
    private lateinit var playerSelectionAdapter: PlayerSelectionAdapter
    private lateinit var courtAdapter: CourtAdapter

    // --- State Management ---
    private val allPlayers = mutableListOf<Player>()
    private val initialSelectedPlayers = mutableSetOf<Player>()
    private val currentCourts = mutableListOf<Court>()
    private var restingPlayers = LinkedList<Player>()

    // Shuffling Algorithm State
    private val partnershipHistory = mutableMapOf<String, Int>()
    private val recentOpponents = mutableMapOf<String, Set<String>>()

    // In-session repeat avoidance counters
    private val partnerCount = mutableMapOf<String, Int>()
    private val opponentCount = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_management)

        db = Firebase.firestore
        auth = Firebase.auth
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "No authenticated user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        playersCollection = db.collection("users").document(uid).collection("players")
        Log.d("PlayersPath", "Using players collection: ${playersCollection.path}")

        initializeUI()
        initializeAdapters()
        setClickListeners()
        listenForPlayerUpdates()
        switchToSetupView()
    }

    private fun initializeUI() {
        titleTextView = findViewById(R.id.textViewTitle)

        // Setup Views
        setupContainer = findViewById(R.id.setupContainer)
        playersRecyclerView = findViewById(R.id.recyclerViewPlayers)
        courtCountEditText = findViewById(R.id.editTextCourtCount)
        startSessionButton = findViewById(R.id.buttonStartSession)

        // Game Views
        restingPlayersTextView = findViewById(R.id.textViewRestingPlayers)
        courtsRecyclerView = findViewById(R.id.recyclerViewCourts)
        gameActionButtons = findViewById(R.id.gameActionButtons)
        addLatePlayerButton = findViewById(R.id.buttonAddLatePlayer)
        addCourtButton = findViewById(R.id.buttonAddCourt)
        endSessionButton = findViewById(R.id.buttonEndSession)

        playersRecyclerView.layoutManager = LinearLayoutManager(this)
        courtsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun initializeAdapters() {
        playerSelectionAdapter = PlayerSelectionAdapter(allPlayers, initialSelectedPlayers) { player, isSelected ->
            if (isSelected) {
                initialSelectedPlayers.add(player)
            } else {
                initialSelectedPlayers.remove(player)
            }
        }
        courtAdapter = CourtAdapter(currentCourts, this::handleGameFinished, this::showEditCourtDialog)

        playersRecyclerView.adapter = playerSelectionAdapter
        courtsRecyclerView.adapter = courtAdapter
    }

    private fun setClickListeners() {
        val addPlayerButton = findViewById<Button>(R.id.buttonAddPlayer)
        val playerNameEditText = findViewById<EditText>(R.id.editTextPlayerName)

        addPlayerButton.setOnClickListener {
            val playerName = playerNameEditText.text.toString().trim()
            if (playerName.isNotEmpty()) {
                addPlayerToFirestore(playerName)
                playerNameEditText.text.clear()
            }
        }
        startSessionButton.setOnClickListener { startSession() }
        addLatePlayerButton.setOnClickListener { showAddLatePlayerDialog() }
        addCourtButton.setOnClickListener { addCourt() }
        endSessionButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Are you sure you want to end the current session? All court progress will be lost.")
                .setPositiveButton("End Session") { _, _ ->
                    switchToSetupView()
                    Toast.makeText(this, "Session Ended", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun listenForPlayerUpdates() {
        playersCollection.orderBy("name").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Firestore", "Listen failed.", e)
                return@addSnapshotListener
            }
            val currentlySelectedIds = initialSelectedPlayers.map { it.id }.toSet()
            allPlayers.clear()
            initialSelectedPlayers.clear()

            snapshots?.forEach { doc ->
                val player = doc.toObject(Player::class.java).copy(id = doc.id)
                allPlayers.add(player)
                if (currentlySelectedIds.contains(player.id)) {
                    initialSelectedPlayers.add(player)
                }
            }
            if (setupContainer.visibility == View.VISIBLE) {
                playerSelectionAdapter.notifyDataSetChanged()
            } else {
                courtAdapter.notifyDataSetChanged()
                updateRestingPlayersView()
            }
        }
    }

    private fun switchToSetupView() {
        titleTextView.text = "Session Setup"
        setupContainer.visibility = View.VISIBLE
        startSessionButton.visibility = View.VISIBLE
        restingPlayersTextView.visibility = View.GONE
        courtsRecyclerView.visibility = View.GONE
        gameActionButtons.visibility = View.GONE

        currentCourts.clear()
        restingPlayers.clear()
        initialSelectedPlayers.clear()
        partnershipHistory.clear()
        recentOpponents.clear()
        partnerCount.clear()
        opponentCount.clear()

        playerSelectionAdapter.notifyDataSetChanged()
    }

    private fun switchToGameView() {
        titleTextView.text = "Active Courts"
        setupContainer.visibility = View.GONE
        startSessionButton.visibility = View.GONE
        restingPlayersTextView.visibility = View.VISIBLE
        courtsRecyclerView.visibility = View.VISIBLE
        gameActionButtons.visibility = View.VISIBLE

        courtAdapter.notifyDataSetChanged()
        updateRestingPlayersView()
    }

    private fun startSession() {
        val selectedPlayerIds = initialSelectedPlayers.map { it.id }.toSet()
        val playersForSession = allPlayers.filter { it.id in selectedPlayerIds }

        val courtCount = courtCountEditText.text.toString().toIntOrNull() ?: 0

        if (courtCount <= 0) {
            Toast.makeText(this, "Please enter a valid number of courts.", Toast.LENGTH_SHORT).show()
            return
        }
        val requiredPlayers = courtCount * 4
        if (playersForSession.size < requiredPlayers) {
            Toast.makeText(this, "Not enough players for $courtCount court(s). Need $requiredPlayers, selected ${playersForSession.size}.", Toast.LENGTH_LONG).show()
            return
        }

        val playerPool = playersForSession.shuffled().toMutableList()

        currentCourts.clear()
        for (i in 1..courtCount) {
            if (playerPool.size < 4) break
            val courtPlayers = playerPool.take(4)
            playerPool.removeAll(courtPlayers)
            val teams = generateTeamsForCourt(courtPlayers)
            currentCourts.add(Court(teams, i))
        }

        restingPlayers = LinkedList(playerPool)

        switchToGameView()
    }

    private fun updatePlayerStats(winners: List<Player>, losers: List<Player>) {
        db.runTransaction { transaction ->
            val allGamePlayers = winners + losers
            val snapshots = mutableMapOf<String, DocumentSnapshot>()

            for (player in allGamePlayers) {
                val playerDocRef = playersCollection.document(player.id)
                snapshots[player.id] = transaction.get(playerDocRef)
            }

            for (player in allGamePlayers) {
                val snapshot = snapshots[player.id]
                if (snapshot == null || !snapshot.exists()) {
                    throw Exception("Document for player ${player.name} with ID ${player.id} does not exist!")
                }

                val currentWins = snapshot.getLong("wins") ?: 0L
                val currentLosses = snapshot.getLong("losses") ?: 0L

                var newWins = currentWins
                var newLosses = currentLosses

                if (winners.any { it.id == player.id }) {
                    newWins++
                } else {
                    newLosses++
                }

                val newGamesPlayed = newWins + newLosses
                val newWinRate = if (newGamesPlayed > 0) newWins.toDouble() / newGamesPlayed else 0.0

                val updates = mapOf(
                    "wins" to newWins,
                    "losses" to newLosses,
                    "gamesPlayed" to newGamesPlayed,
                    "winrate" to newWinRate
                )
                transaction.update(snapshot.reference, updates)
            }
            null
        }.addOnSuccessListener {
            Log.d("PlayerStats", "Transaction successful: Player stats updated.")
        }.addOnFailureListener { e ->
            Log.e("PlayerStats", "Transaction failed.", e)
            Toast.makeText(this, "Failed to update player stats: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleGameFinished(winners: List<Player>, losers: List<Player>, courtIndex: Int) {
        updatePlayerStats(winners, losers)
        updatePairingStats(winners, losers)

        val winnerKey = winners.map { it.name }.sorted().joinToString("|")
        val loserKey = losers.map { it.name }.sorted().joinToString("|")
        partnershipHistory[winnerKey] = partnershipHistory.getOrDefault(winnerKey, 0) + 1
        partnershipHistory[loserKey] = partnershipHistory.getOrDefault(loserKey, 0) + 1

        val finishedPlayers = winners + losers
        restingPlayers.addAll(finishedPlayers)

        currentCourts[courtIndex].teams = null

        refillEmptyCourts()

        updateRestingPlayersView()
    }

    private fun refillEmptyCourts() {
        for (court in currentCourts) {
            if (court.teams == null && restingPlayers.size >= 4) {
                val newCourtPlayers = (0..3).map { restingPlayers.removeFirst() }
                court.teams = generateTeamsForCourt(newCourtPlayers)
            }
        }
        courtAdapter.notifyDataSetChanged()
    }

    private fun updateRestingPlayersView() {
        if (restingPlayers.isEmpty()) {
            restingPlayersTextView.text = "Resting: None"
        } else {
            val restingNames = restingPlayers.joinToString(", ") { it.name }
            restingPlayersTextView.text = "Resting: $restingNames"
        }
        addCourtButton.isEnabled = restingPlayers.size >= 4
    }

    private fun addPlayerToFirestore(playerName: String) {
        val normalizedName = playerName.lowercase()
        val docRef = playersCollection.document(normalizedName)

        docRef.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                val player = Player(name = playerName, wins = 0, losses = 0, gamesPlayed = 0, winrate = 0.0)
                docRef.set(player)
                    .addOnSuccessListener { Toast.makeText(this, "$playerName added.", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            } else {
                Toast.makeText(this, "$playerName already exists.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper functions for new shuffling algorithm
    private fun rating(player: Player): Double {
        return if (player.gamesPlayed < 10) 0.5 else player.winrate
    }

    private fun getPartnerCount(playerId1: String, playerId2: String): Int {
        val key = listOf(playerId1, playerId2).sorted().joinToString("|")
        return partnerCount.getOrDefault(key, 0)
    }

    private fun getOpponentCount(playerId1: String, playerId2: String): Int {
        val key = listOf(playerId1, playerId2).sorted().joinToString("|")
        return opponentCount.getOrDefault(key, 0)
    }

    private fun incPartnerCount(playerId1: String, playerId2: String) {
        val key = listOf(playerId1, playerId2).sorted().joinToString("|")
        partnerCount[key] = partnerCount.getOrDefault(key, 0) + 1
    }

    private fun incOpponentCount(playerId1: String, playerId2: String) {
        val key = listOf(playerId1, playerId2).sorted().joinToString("|")
        opponentCount[key] = opponentCount.getOrDefault(key, 0) + 1
    }

    private fun updatePairingStats(winners: List<Player>, losers: List<Player>) {
        // Update partner counts for teammates
        if (winners.size >= 2) {
            incPartnerCount(winners[0].id, winners[1].id)
        }
        if (losers.size >= 2) {
            incPartnerCount(losers[0].id, losers[1].id)
        }

        // Update opponent counts for all cross-team matchups
        for (winner in winners) {
            for (loser in losers) {
                incOpponentCount(winner.id, loser.id)
            }
        }
    }

    private fun pairingCost(players: List<Player>, teamA: List<Player>, teamB: List<Player>): Double {
        if (teamA.size != 2 || teamB.size != 2) return Double.MAX_VALUE

        // Team balance cost (difference in average rating)
        val teamARating = teamA.map { rating(it) }.average()
        val teamBRating = teamB.map { rating(it) }.average()
        val balanceCost = abs(teamARating - teamBRating)

        // Partner repeat penalty
        val partnerPenalty = getPartnerCount(teamA[0].id, teamA[1].id) * PARTNER_REPEAT_PENALTY +
                            getPartnerCount(teamB[0].id, teamB[1].id) * PARTNER_REPEAT_PENALTY

        // Opponent repeat penalty
        var opponentPenalty = 0.0
        for (playerA in teamA) {
            for (playerB in teamB) {
                opponentPenalty += getOpponentCount(playerA.id, playerB.id) * OPPONENT_REPEAT_PENALTY
            }
        }

        return balanceCost + partnerPenalty + opponentPenalty
    }

    private fun chooseBestPairingOfFour(players: List<Player>): Pair<List<Player>, List<Player>>? {
        if (players.size != 4) return null

        val p0 = players[0]
        val p1 = players[1]
        val p2 = players[2]
        val p3 = players[3]

        // Three possible pairings: (A,B)-(C,D), (A,C)-(B,D), (A,D)-(B,C)
        val pairing1 = Pair(listOf(p0, p1), listOf(p2, p3))
        val pairing2 = Pair(listOf(p0, p2), listOf(p1, p3))
        val pairing3 = Pair(listOf(p0, p3), listOf(p1, p2))

        val cost1 = pairingCost(players, pairing1.first, pairing1.second)
        val cost2 = pairingCost(players, pairing2.first, pairing2.second)
        val cost3 = pairingCost(players, pairing3.first, pairing3.second)

        return when {
            cost1 <= cost2 && cost1 <= cost3 -> pairing1
            cost2 <= cost3 -> pairing2
            else -> pairing3
        }
    }

    private fun generateTeamsForCourt(players: List<Player>): Pair<List<Player>, List<Player>>? {
        return chooseBestPairingOfFour(players)
    }

    private fun showAddLatePlayerDialog() {
        val activePlayerIds = mutableSetOf<String>()
        restingPlayers.forEach { activePlayerIds.add(it.id) }
        currentCourts.forEach { court ->
            court.teams?.let { (teamA, teamB) ->
                teamA.forEach { activePlayerIds.add(it.id) }
                teamB.forEach { activePlayerIds.add(it.id) }
            }
        }

        val availablePlayers = allPlayers.filter { it.id !in activePlayerIds }
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_late_player, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupExistingPlayers)
        val newPlayerNameEditText = dialogView.findViewById<EditText>(R.id.editTextNewPlayerName)
        val addNewPlayerButton = dialogView.findViewById<Button>(R.id.buttonAddNewPlayer)
        val existingPlayersTitle = dialogView.findViewById<TextView>(R.id.textViewExistingPlayersTitle)

        if (availablePlayers.isEmpty()) {
            existingPlayersTitle.visibility = View.GONE
            chipGroup.visibility = View.GONE
        } else {
            availablePlayers.forEach { player ->
                val chip = Chip(this)
                chip.text = player.name
                chip.isCheckable = true
                chip.tag = player
                chipGroup.addView(chip)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Late Players")
            .setView(dialogView)
            .setPositiveButton("Add Selected") { _, _ ->
                val selectedPlayers = mutableListOf<Player>()
                for (i in 0 until chipGroup.childCount) {
                    val chip = chipGroup.getChildAt(i) as Chip
                    if (chip.isChecked) {
                        selectedPlayers.add(chip.tag as Player)
                    }
                }

                if (selectedPlayers.isNotEmpty()) {
                    restingPlayers.addAll(selectedPlayers)
                    updateRestingPlayersView()
                    val playerNames = selectedPlayers.joinToString(", ") { it.name }
                    Toast.makeText(this, "$playerNames added to the resting queue.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        addNewPlayerButton.setOnClickListener {
            val playerName = newPlayerNameEditText.text.toString().trim()
            if (playerName.isNotEmpty()) {
                addLatePlayer(playerName) {
                    dialog.dismiss()
                    showAddLatePlayerDialog()
                }
            } else {
                Toast.makeText(this, "Player name cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun addLatePlayer(playerName: String, onSuccess: () -> Unit) {
        val normalizedName = playerName.lowercase()
        val playerExists = allPlayers.any { it.name.equals(normalizedName, ignoreCase = true) }

        if (playerExists) {
            Toast.makeText(this, "$playerName already exists.", Toast.LENGTH_SHORT).show()
            return
        }

        val newPlayer = Player(name = playerName, wins = 0, losses = 0, gamesPlayed = 0, winrate = 0.0)
        playersCollection.document(normalizedName).set(newPlayer)
            .addOnSuccessListener {
                val playerWithId = newPlayer.copy(id = normalizedName)
                allPlayers.add(playerWithId)
                restingPlayers.add(playerWithId)
                updateRestingPlayersView()
                Toast.makeText(this, "$playerName added and is now resting.", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding player: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun addCourt() {
        if (restingPlayers.size >= 4) {
            val newCourtPlayers = (0..3).map { restingPlayers.removeFirst() }
            val newTeams = generateTeamsForCourt(newCourtPlayers)
            val newCourtNumber = (currentCourts.maxOfOrNull { it.courtNumber } ?: 0) + 1
            currentCourts.add(Court(newTeams, newCourtNumber))

            courtAdapter.notifyDataSetChanged()
            updateRestingPlayersView()
            Toast.makeText(this, "Court $newCourtNumber added.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Need at least 4 resting players to add a new court.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditCourtDialog(courtIndex: Int) {
        val court = currentCourts.getOrNull(courtIndex) ?: return
        val (teamA, teamB) = court.teams ?: Pair(emptyList(), emptyList())

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_court, null)
        val rvTeamA = dialogView.findViewById<RecyclerView>(R.id.recyclerViewTeamA)
        val rvTeamB = dialogView.findViewById<RecyclerView>(R.id.recyclerViewTeamB)
        val rvResting = dialogView.findViewById<RecyclerView>(R.id.recyclerViewResting)
        val restingTitle = dialogView.findViewById<TextView>(R.id.textViewRestingTitle)
        val deleteButton = dialogView.findViewById<Button>(R.id.buttonDeleteCourt)

        val teamAMutable = teamA.toMutableList()
        val teamBMutable = teamB.toMutableList()
        val restingMutable = restingPlayers.toMutableList()

        val adapterA = EditCourtPlayerAdapter(teamAMutable)
        val adapterB = EditCourtPlayerAdapter(teamBMutable)
        val adapterResting = EditCourtPlayerAdapter(restingMutable)

        var firstSelection: Player? = null
        var firstList: MutableList<Player>? = null
        val allAdapters = listOf(adapterA, adapterB, adapterResting)

        fun handleSelection(player: Player, list: MutableList<Player>, clickedAdapter: EditCourtPlayerAdapter) {
            if (firstSelection == null) {
                firstSelection = player
                firstList = list
                allAdapters.forEach { it.selectedPlayer = player; it.notifyDataSetChanged() }
            } else {
                if (firstSelection != player) {
                    val player1 = firstSelection!!
                    val list1 = firstList!!

                    val index1 = list1.indexOf(player1)
                    val index2 = list.indexOf(player)

                    if (index1 != -1 && index2 != -1) {
                        list1[index1] = player
                        list[index2] = player1
                    }
                }
                firstSelection = null
                firstList = null
                allAdapters.forEach { it.selectedPlayer = null; it.notifyDataSetChanged() }
            }
        }

        adapterA.onPlayerSelected = { player -> handleSelection(player, teamAMutable, adapterA) }
        rvTeamA.layoutManager = LinearLayoutManager(this)
        rvTeamA.adapter = adapterA

        adapterB.onPlayerSelected = { player -> handleSelection(player, teamBMutable, adapterB) }
        rvTeamB.layoutManager = LinearLayoutManager(this)
        rvTeamB.adapter = adapterB

        if (restingMutable.isEmpty()) {
            restingTitle.visibility = View.GONE
            rvResting.visibility = View.GONE
        } else {
            adapterResting.onPlayerSelected = { player -> handleSelection(player, restingMutable, adapterResting) }
            rvResting.layoutManager = LinearLayoutManager(this)
            rvResting.adapter = adapterResting
        }

        val mainDialog = AlertDialog.Builder(this)
            .setTitle("Edit Court ${court.courtNumber}")
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                currentCourts[courtIndex].teams = if (teamAMutable.isNotEmpty() || teamBMutable.isNotEmpty()) {
                    Pair(teamAMutable, teamBMutable)
                } else {
                    null
                }
                restingPlayers = LinkedList(restingMutable)
                courtAdapter.notifyItemChanged(courtIndex)
                updateRestingPlayersView()
            }
            .setNeutralButton("Cancel", null)
            .create()

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Court")
                .setMessage("Are you sure you want to delete Court ${court.courtNumber}? All players on this court will be moved to the resting queue.")
                .setPositiveButton("Delete") { _, _ ->
                    val playersToRest = currentCourts[courtIndex].teams?.toList()?.flatMap { it } ?: emptyList()
                    restingPlayers.addAll(playersToRest)
                    currentCourts.removeAt(courtIndex)
                    courtAdapter.notifyDataSetChanged()
                    updateRestingPlayersView()
                    mainDialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        mainDialog.show()
    }
}