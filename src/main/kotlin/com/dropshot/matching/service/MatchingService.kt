package com.dropshot.matching.service

import com.dropshot.matching.domain.GroupMatch
import com.dropshot.matching.domain.GroupMatchMember
import com.dropshot.matching.domain.Participant
import com.dropshot.matching.domain.gradeScore
import com.dropshot.matching.dto.GroupMatchResponse
import com.dropshot.matching.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import kotlin.math.abs
import kotlin.random.Random

@Service
class MatchingService(
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val partnerPairRepository: PartnerPairRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val eventService: EventService
) {
    data class UnitPlayers(val players: List<Participant>)
    data class GroupPlan(val roundNo: Int, val groupNo: Int, val players: List<Participant>)

    @Transactional
    fun generate(eventId: Long): List<GroupMatchResponse> {
        eventService.clearGroups(eventId)
        val event = eventRepository.findById(eventId).orElseThrow { notFound("event", eventId) }
        val participants = participantRepository.findByEventIdOrderByNameAsc(eventId)
        require(participants.size >= 4) { "참가자는 최소 4명 이상 필요합니다." }

        val plans = buildPlans(eventId, participants, 1, event.gamesPerPlayer, emptyList())
        savePlans(eventId, plans)
        return eventService.loadGroups(eventId)
    }

    @Transactional
    fun replan(eventId: Long): List<GroupMatchResponse> {
        val event = eventRepository.findById(eventId).orElseThrow { notFound("event", eventId) }
        val existing = groupMatchRepository.findByEventIdOrderByRoundNoAscGroupNoAsc(eventId)
        if (existing.isEmpty()) return generate(eventId)
        val progressedRound = existing.filter { it.completed }.maxOfOrNull { it.roundNo } ?: 0
        require(progressedRound < event.gamesPerPlayer) { "이미 모든 라운드가 진행 표시되어 다시 짤 라운드가 없습니다." }

        val kept = eventService.loadGroups(eventId).filter { it.roundNo <= progressedRound }
        groupMatchMemberRepository.deleteMembersByEventIdAndRoundNoGreaterThanEqual(eventId, progressedRound + 1)
        groupMatchRepository.deleteGroupsByEventIdAndRoundNoGreaterThanEqual(eventId, progressedRound + 1)

        val participants = participantRepository.findByEventIdOrderByNameAsc(eventId)
        val keptPlans = kept.map { group ->
            GroupPlan(
                roundNo = group.roundNo,
                groupNo = group.groupNo,
                players = group.members.map { member ->
                    participants.first { it.id == member.participantId }
                }
            )
        }
        val newPlans = buildPlans(eventId, participants, progressedRound + 1, event.gamesPerPlayer, keptPlans)
        savePlans(eventId, newPlans)
        return eventService.loadGroups(eventId)
    }

    private fun savePlans(eventId: Long, plans: List<GroupPlan>) {
        val event = eventRepository.findById(eventId).orElseThrow { notFound("event", eventId) }
        plans.forEach { plan ->
            val group = groupMatchRepository.save(
                GroupMatch(event = event, roundNo = plan.roundNo, groupNo = plan.groupNo)
            )
            plan.players.forEachIndexed { index, participant ->
                groupMatchMemberRepository.save(
                    GroupMatchMember(groupMatch = group, participant = participant, seatNo = index + 1)
                )
            }
        }
    }

    private fun buildPlans(
        eventId: Long,
        participants: List<Participant>,
        startRound: Int,
        endRound: Int,
        basePlans: List<GroupPlan>
    ): List<GroupPlan> {
        val playableCount = (participants.size / 4) * 4
        require(playableCount >= 4) { "참가자는 최소 4명 이상 필요합니다." }

        val pairCounts = buildPairCounts(basePlans)
        val playCounts = participants.associate { it.id to 0 }.toMutableMap()
        basePlans.flatMap { it.players }.forEach { playCounts[it.id] = (playCounts[it.id] ?: 0) + 1 }
        val result = mutableListOf<GroupPlan>()
        val partnerUnits = partnerUnits(eventId, participants)

        for (round in startRound..endRound) {
            val activeUnits = selectActiveUnits(partnerUnits, playCounts, playableCount)
            val groups = buildBestGroups(activeUnits, pairCounts)
            groups.forEachIndexed { index, group ->
                val plan = GroupPlan(round, index + 1, group)
                result += plan
                group.forEach { playCounts[it.id] = (playCounts[it.id] ?: 0) + 1 }
                recordPairs(group, pairCounts)
            }
        }
        return result
    }

    private fun partnerUnits(eventId: Long, participants: List<Participant>): List<UnitPlayers> {
        val byId = participants.associateBy { it.id }
        val partnerMap = mutableMapOf<Long, Long>()
        partnerPairRepository.findByEventId(eventId).forEach {
            partnerMap[it.participantA.id] = it.participantB.id
            partnerMap[it.participantB.id] = it.participantA.id
        }
        val used = mutableSetOf<Long>()
        return participants.mapNotNull { participant ->
            if (!used.add(participant.id)) return@mapNotNull null
            val partner = partnerMap[participant.id]?.let { byId[it] }
            if (partner != null && used.add(partner.id)) UnitPlayers(listOf(participant, partner))
            else UnitPlayers(listOf(participant))
        }
    }

    private fun selectActiveUnits(units: List<UnitPlayers>, playCounts: Map<Long, Int>, playableCount: Int): List<UnitPlayers> {
        val ordered = units.sortedWith(
            compareBy<UnitPlayers> { unit -> unit.players.map { playCounts[it.id] ?: 0 }.average() }
                .thenByDescending { it.players.size }
        )
        for (target in playableCount downTo 4 step 4) {
            val candidates = unitCombinations(ordered, target)
            if (candidates.isNotEmpty()) {
                return candidates.minBy { selected ->
                    val counts = selected.flatMap { it.players }.map { playCounts[it.id] ?: 0 }
                    (counts.max() - counts.min()) * 100 + counts.sum()
                }
            }
        }
        throw IllegalArgumentException("현재 참가자/파트너 조합으로 4명 조를 만들 수 없습니다.")
    }

    private fun buildBestGroups(units: List<UnitPlayers>, pairCounts: Map<String, Int>): List<List<Participant>> {
        val active = units.flatMap { it.players }
        val targetScore = active.sumOf { gradeScore(it.grade) }.toDouble() / (active.size / 4)
        val remaining = units.shuffled().toMutableList()
        val groups = mutableListOf<List<Participant>>()

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val options = sampledCombinations(remaining, 4 - seed.players.size).map { combo ->
                val groupUnits = listOf(seed) + combo
                val players = groupUnits.flatMap { it.players }
                val relation = relationPenalty(players, pairCounts)
                val scoreGap = abs(players.sumOf { gradeScore(it.grade) } - targetScore)
                val shapePenalty = groupShapePenalty(players)
                Triple(groupUnits, players, relation * 18 + scoreGap * 140 + shapePenalty * 160 + Random.nextDouble())
            }.sortedBy { it.third }

            val selected = options.firstOrNull() ?: error("조를 만들 수 없습니다.")
            val selectedIds = selected.first.flatMap { it.players }.map { it.id }.toSet()
            remaining.removeIf { unit -> unit.players.any { it.id in selectedIds } }
            groups += selected.second
        }
        return groups
    }

    private fun sampledCombinations(units: List<UnitPlayers>, targetSize: Int, limit: Int = 240): List<List<UnitPlayers>> {
        val deterministic = unitCombinations(units, targetSize, limit)
        if (deterministic.isNotEmpty()) return deterministic

        val results = mutableListOf<List<UnitPlayers>>()
        val seen = mutableSetOf<String>()
        repeat(limit * 3) {
            if (results.size >= limit) return@repeat
            var size = 0
            val selected = mutableListOf<UnitPlayers>()
            units.shuffled().forEach { unit ->
                if (size + unit.players.size <= targetSize) {
                    selected += unit
                    size += unit.players.size
                }
            }
            if (size == targetSize) {
                val key = selected.flatMap { it.players }.map { it.id }.sorted().joinToString(":")
                if (seen.add(key)) results += selected
            }
        }
        return results
    }

    private fun unitCombinations(units: List<UnitPlayers>, targetSize: Int, limit: Int = 240): List<List<UnitPlayers>> {
        val results = mutableListOf<List<UnitPlayers>>()

        fun visit(index: Int, selected: MutableList<UnitPlayers>, selectedSize: Int) {
            if (results.size >= limit || selectedSize > targetSize) return
            if (selectedSize == targetSize) {
                results += selected.toList()
                return
            }
            if (index >= units.size) return

            val remainingSize = units.drop(index).sumOf { it.players.size }
            if (selectedSize + remainingSize < targetSize) return

            selected += units[index]
            visit(index + 1, selected, selectedSize + units[index].players.size)
            selected.removeAt(selected.lastIndex)
            visit(index + 1, selected, selectedSize)
        }

        visit(0, mutableListOf(), 0)
        return results
    }

    private fun groupShapePenalty(players: List<Participant>): Int {
        val scores = players.map { gradeScore(it.grade) }.sortedDescending()
        val topLead = scores[0] - scores[1]
        val lowerCluster = scores[1] - scores[3]
        val topTwoGap = scores[1] - scores[2]
        if (topLead >= 2 && lowerCluster <= 1) return topLead * 3
        if (topLead >= 3) return topLead
        if (topTwoGap >= 3) return 0
        return 0
    }

    private fun relationPenalty(players: List<Participant>, pairCounts: Map<String, Int>): Int {
        var penalty = 0
        for (i in players.indices) {
            for (j in i + 1 until players.size) {
                penalty += (pairCounts[pairKey(players[i].id, players[j].id)] ?: 0) * 28
            }
        }
        return penalty
    }

    private fun buildPairCounts(plans: List<GroupPlan>): MutableMap<String, Int> {
        val counts = mutableMapOf<String, Int>()
        plans.forEach { recordPairs(it.players, counts) }
        return counts
    }

    private fun recordPairs(players: List<Participant>, counts: MutableMap<String, Int>) {
        for (i in players.indices) {
            for (j in i + 1 until players.size) {
                val key = pairKey(players[i].id, players[j].id)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
    }

    private fun pairKey(a: Long, b: Long) = listOf(a, b).sorted().joinToString(":")

    private fun notFound(resource: String, id: Long) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "$resource not found: $id")
}
