package com.ghancaballes.blacksheep

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class CourtAdapter(
    private var courts: List<Court>,
    private val onWinnerSelected: (winners: List<Player>, losers: List<Player>, courtIndex: Int) -> Unit,
    private val onEditCourtClicked: (courtIndex: Int) -> Unit,
    private val onMatchRecordingFailed: (courtIndex: Int) -> Unit
) : RecyclerView.Adapter<CourtAdapter.CourtViewHolder>() {

    companion object {
        private const val MIN_RANKED_GAMES = 10
        private const val TAG = "CourtAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourtViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.court_item, parent, false)
        return CourtViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourtViewHolder, position: Int) {
        val court = courts[position]
        holder.bind(court, position)
    }

    override fun getItemCount(): Int = courts.size

    fun updateCourts(newCourts: List<Court>) {
        this.courts = newCourts
        notifyDataSetChanged()
    }

    fun reEnableWinnerButtons(courtIndex: Int) {
        // Find the ViewHolder for the specific court and re-enable its buttons
        // This will be called when match recording fails
        notifyItemChanged(courtIndex)
    }

    inner class CourtViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // References to all the views in the layout
        private val courtName: TextView = itemView.findViewById(R.id.courtName)
        private val editCourtButton: Button = itemView.findViewById(R.id.buttonEditCourt)
        private val courtGrid: View = itemView.findViewById(R.id.courtGrid)
        private val emptyCourtMessage: TextView = itemView.findViewById(R.id.textViewEmptyCourt)

        // Player name views
        private val player1Name: TextView = itemView.findViewById(R.id.player1_name)
        private val player2Name: TextView = itemView.findViewById(R.id.player2_name)
        private val player3Name: TextView = itemView.findViewById(R.id.player3_name)
        private val player4Name: TextView = itemView.findViewById(R.id.player4_name)

        // Winner buttons
        private val buttonWinnerTeamA: Button = itemView.findViewById(R.id.buttonWinnerTeamA)
        private val buttonWinnerTeamB: Button = itemView.findViewById(R.id.buttonWinnerTeamB)

        fun bind(court: Court, position: Int) {
            courtName.text = "Court ${court.courtNumber}"

            val teamA = court.teams?.first
            val teamB = court.teams?.second

            editCourtButton.setOnClickListener {
                onEditCourtClicked(position)
            }

            if (teamA != null && teamA.size == 2 && teamB != null && teamB.size == 2) {
                // Court has players, show the grid and hide the empty message
                courtGrid.visibility = View.VISIBLE
                emptyCourtMessage.visibility = View.GONE

                // Always re-enable winner buttons on bind to avoid recycled views remaining disabled
                buttonWinnerTeamA.isEnabled = true
                buttonWinnerTeamB.isEnabled = true

                // Map players to quadrants
                val a1 = teamA[0]
                val a2 = teamA[1]
                val b1 = teamB[0]
                val b2 = teamB[1]

                player1Name.text = a1.name // Team A, Player 1 (Top Left)
                player2Name.text = a2.name // Team A, Player 2 (Bottom Left)
                player3Name.text = b1.name // Team B, Player 1 (Top Right)
                player4Name.text = b2.name // Team B, Player 2 (Bottom Right)

                // Set highlight backgrounds based on rank + winrate threshold
                setPlayerBackground(player1Name, a1)
                setPlayerBackground(player2Name, a2)
                setPlayerBackground(player3Name, b1)
                setPlayerBackground(player4Name, b2)

                // Set winner button listeners with confirmation dialog
                buttonWinnerTeamA.setOnClickListener { showWinnerConfirmationDialog(teamA, teamB, position, "Team A") }
                buttonWinnerTeamB.setOnClickListener { showWinnerConfirmationDialog(teamB, teamA, position, "Team B") }
            } else {
                // Court is empty or not properly formed, hide the grid and show the message
                courtGrid.visibility = View.GONE
                emptyCourtMessage.visibility = View.VISIBLE
            }
        }

        private fun showWinnerConfirmationDialog(winners: List<Player>, losers: List<Player>, position: Int, teamName: String) {
            val context = itemView.context
            val winnerNames = winners.joinToString(" & ") { it.name }
            val loserNames = losers.joinToString(" & ") { it.name }
            
            AlertDialog.Builder(context)
                .setTitle("Confirm Winner")
                .setMessage("Confirm $teamName ($winnerNames) won against ($loserNames)?")
                .setPositiveButton("Confirm") { _, _ ->
                    // Disable both buttons to guard against double taps
                    buttonWinnerTeamA.isEnabled = false
                    buttonWinnerTeamB.isEnabled = false
                    
                    // Call the winner selection callback
                    onWinnerSelected(winners, losers, position)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun reEnableWinnerButtons() {
            buttonWinnerTeamA.isEnabled = true
            buttonWinnerTeamB.isEnabled = true
        }

        private fun setPlayerBackground(textView: TextView, player: Player) {
            // Debug log to verify values at bind time
            Log.d(TAG, "Coloring ${player.name} gp=${player.gamesPlayed} wr=${player.winrate}")

            val context = textView.context
            // Only establish rank after MIN_RANKED_GAMES
            val colorRes = if (player.gamesPlayed < MIN_RANKED_GAMES) {
                android.R.color.transparent
            } else {
                when {
                    player.winrate >= 0.80 -> R.color.skill_high    // Bright Red
                    player.winrate >= 0.60 -> R.color.skill_medium  // Bright Orange
                    player.winrate >= 0.50 -> R.color.skill_low     // Bright Yellow
                    else -> android.R.color.transparent
                }
            }
            textView.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        }
    }
}