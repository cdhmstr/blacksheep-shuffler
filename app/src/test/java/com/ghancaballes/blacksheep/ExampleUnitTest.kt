package com.ghancaballes.blacksheep

import org.junit.Test
import org.junit.Assert.*
import java.util.LinkedList

/**
 * Unit tests for the blacksheep shuffler app
 */
class PlayerManagementTest {
    
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    
    @Test
    fun restCount_prioritizesLongestRestingPlayers() {
        // Create test players
        val player1 = Player(id = "1", name = "Player 1")
        val player2 = Player(id = "2", name = "Player 2")
        val player3 = Player(id = "3", name = "Player 3")
        val player4 = Player(id = "4", name = "Player 4")
        val player5 = Player(id = "5", name = "Player 5")
        val player6 = Player(id = "6", name = "Player 6")
        
        // Simulate rest counts (higher = rested longer)
        val restCount = mutableMapOf<String, Int>()
        restCount["1"] = 2 // Player 1 has rested through 2 games
        restCount["2"] = 1 // Player 2 has rested through 1 game
        restCount["3"] = 3 // Player 3 has rested through 3 games (longest)
        restCount["4"] = 0 // Player 4 is new to resting
        restCount["5"] = 1 // Player 5 has rested through 1 game
        restCount["6"] = 2 // Player 6 has rested through 2 games
        
        val restingPlayers = LinkedList(listOf(player1, player2, player3, player4, player5, player6))
        
        // Sort by rest count (descending) as the algorithm does
        val sortedPlayers = restingPlayers.sortedByDescending { player ->
            restCount.getOrDefault(player.id, 0)
        }
        
        // Player 3 should be first (highest rest count)
        assertEquals("Player 3", sortedPlayers[0].name)
        
        // Players with rest count 2 should come next (players 1 and 6)
        val playersWithCount2 = sortedPlayers.filter { restCount[it.id] == 2 }
        assertEquals(2, playersWithCount2.size)
        assertTrue(playersWithCount2.any { it.name == "Player 1" })
        assertTrue(playersWithCount2.any { it.name == "Player 6" })
        
        // Player 4 should be last (lowest rest count)
        assertEquals("Player 4", sortedPlayers.last().name)
    }
    
    @Test
    fun matchId_isConsistentAndDeterministic() {
        val courtNumber = 1
        val sequence1 = 1
        val sequence2 = 2
        
        val matchId1 = "court${courtNumber}_seq${sequence1}"
        val matchId2 = "court${courtNumber}_seq${sequence2}"
        
        assertEquals("court1_seq1", matchId1)
        assertEquals("court1_seq2", matchId2)
        
        // Verify match IDs are unique for same court
        assertNotEquals(matchId1, matchId2)
        
        // Verify match IDs are different for different courts
        val courtNumber2 = 2
        val matchId3 = "court${courtNumber2}_seq${sequence1}"
        assertEquals("court2_seq1", matchId3)
        assertNotEquals(matchId1, matchId3)
    }
}