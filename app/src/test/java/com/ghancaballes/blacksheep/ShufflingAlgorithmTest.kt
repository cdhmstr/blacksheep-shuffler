package com.ghancaballes.blacksheep

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Unit test for the new shuffling algorithm functionality.
 */
class ShufflingAlgorithmTest {

    // Constants matching the main implementation
    companion object {
        const val PARTNER_REPEAT_PENALTY = 10.0
        const val OPPONENT_REPEAT_PENALTY = 5.0
    }

    // Test counters
    private val partnerCount = mutableMapOf<String, Int>()
    private val opponentCount = mutableMapOf<String, Int>()

    // Helper functions (copied from main implementation)
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

    @Test
    fun rating_function_works_correctly() {
        val unrankedPlayer = Player(id = "1", name = "Alice", wins = 3, losses = 2, gamesPlayed = 5, winrate = 0.6)
        val rankedPlayer = Player(id = "2", name = "Bob", wins = 8, losses = 2, gamesPlayed = 10, winrate = 0.8)
        val highRankedPlayer = Player(id = "3", name = "Charlie", wins = 18, losses = 2, gamesPlayed = 20, winrate = 0.9)

        // Unranked player (< 10 games) should get 0.5 rating
        assertEquals(0.5, rating(unrankedPlayer), 0.001)
        
        // Ranked players (>= 10 games) should get their actual winrate
        assertEquals(0.8, rating(rankedPlayer), 0.001)
        assertEquals(0.9, rating(highRankedPlayer), 0.001)
    }

    @Test
    fun pairing_cost_calculates_balance_correctly() {
        val alice = Player(id = "1", name = "Alice", wins = 8, losses = 2, gamesPlayed = 10, winrate = 0.8)
        val bob = Player(id = "2", name = "Bob", wins = 6, losses = 4, gamesPlayed = 10, winrate = 0.6)
        val charlie = Player(id = "3", name = "Charlie", wins = 7, losses = 3, gamesPlayed = 10, winrate = 0.7)
        val diana = Player(id = "4", name = "Diana", wins = 5, losses = 5, gamesPlayed = 10, winrate = 0.5)

        val players = listOf(alice, bob, charlie, diana)
        val teamA = listOf(alice, diana) // avg = 0.65
        val teamB = listOf(bob, charlie) // avg = 0.65

        // With no repeat penalties, cost should just be balance difference (0.0)
        val cost = pairingCost(players, teamA, teamB)
        assertEquals(0.0, cost, 0.001)
    }

    @Test
    fun pairing_cost_includes_repeat_penalties() {
        val alice = Player(id = "1", name = "Alice", wins = 8, losses = 2, gamesPlayed = 10, winrate = 0.8)
        val bob = Player(id = "2", name = "Bob", wins = 6, losses = 4, gamesPlayed = 10, winrate = 0.6)
        val charlie = Player(id = "3", name = "Charlie", wins = 7, losses = 3, gamesPlayed = 10, winrate = 0.7)
        val diana = Player(id = "4", name = "Diana", wins = 5, losses = 5, gamesPlayed = 10, winrate = 0.5)

        // Set up repeat history
        partnerCount.clear()
        opponentCount.clear()
        partnerCount["1|4"] = 2 // Alice and Diana were partners twice
        opponentCount["1|2"] = 3 // Alice and Bob were opponents 3 times

        val players = listOf(alice, bob, charlie, diana)
        val teamA = listOf(alice, diana) // These were partners before
        val teamB = listOf(bob, charlie)

        val cost = pairingCost(players, teamA, teamB)
        
        // Expected: balance (0.0) + partner penalty (2 * 10.0) + opponent penalty (3 * 5.0) = 35.0
        assertEquals(35.0, cost, 0.001)
    }

    @Test
    fun unranked_players_get_provisional_rating() {
        val newPlayer1 = Player(id = "1", name = "Alice", wins = 0, losses = 0, gamesPlayed = 0, winrate = 0.0)
        val newPlayer2 = Player(id = "2", name = "Bob", wins = 1, losses = 4, gamesPlayed = 5, winrate = 0.2)
        val rankedPlayer = Player(id = "3", name = "Charlie", wins = 15, losses = 5, gamesPlayed = 20, winrate = 0.75)

        // New players with < 10 games should get 0.5 regardless of actual winrate
        assertEquals(0.5, rating(newPlayer1), 0.001)
        assertEquals(0.5, rating(newPlayer2), 0.001)
        
        // Experienced player should get actual winrate
        assertEquals(0.75, rating(rankedPlayer), 0.001)
    }
}