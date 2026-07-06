package com.dropshot.matching.service

import com.dropshot.matching.domain.CompletedCourtGame
import com.dropshot.matching.domain.CompletedCourtGameMember
import com.dropshot.matching.domain.MatchingEvent
import com.dropshot.matching.domain.Participant
import com.dropshot.matching.domain.PartnerPair
import com.dropshot.matching.dto.*
import com.dropshot.matching.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val memberRepository: MemberRepository,
    private val participantRepository: ParticipantRepository,
    private val partnerPairRepository: PartnerPairRepository,
    private val completedCourtGameRepository: CompletedCourtGameRepository,
    private val completedCourtGameMemberRepository: CompletedCourtGameMemberRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository
) {
    @Transactional
    fun create(request: EventRequest): EventResponse {
        val event = eventRepository.save(
            MatchingEvent(name = request.name.ifBlank { "DROP SHOT 모임" }, gamesPerPlayer = 10)
        )
        return event.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(): List<EventResponse> =
        eventRepository.findAllByOrderByCreatedAtDesc().map { it.toResponse() }

    @Transactional
    fun finish(eventId: Long): EventResponse {
        val event = findEvent(eventId)
        event.endedAt = event.endedAt ?: java.time.Instant.now()
        event.gameStarted = false
        return event.toResponse()
    }

    @Transactional(readOnly = true)
    fun detail(eventId: Long): EventDetailResponse {
        val event = findEvent(eventId)
        return EventDetailResponse(
            event = event.toResponse(),
            participants = participantRepository.findByEventIdOrderByNameAsc(eventId).map { it.toResponse() },
            partners = partnerPairRepository.findByEventId(eventId).map { it.toResponse() },
            completedGames = loadCompletedGames(eventId),
            state = event.toStateResponse(),
            groups = loadGroups(eventId)
        )
    }

    @Transactional
    fun updateState(eventId: Long, request: EventStateRequest): EventStateResponse {
        val event = findEvent(eventId)
        event.courtCount = request.courtCount.coerceIn(1, 8)
        event.gameStarted = request.gameStarted
        event.courtAssignmentsJson = request.courtAssignments.ifBlank { "{}" }
        event.courtNamesJson = request.courtNames.ifBlank { "{}" }
        event.waitingSinceJson = request.waitingSince.ifBlank { "{}" }
        event.restingParticipantIdsJson = request.restingParticipantIds.ifBlank { "[]" }
        event.exitedParticipantIdsJson = request.exitedParticipantIds.ifBlank { "[]" }
        return event.toStateResponse()
    }

    @Transactional
    fun addParticipant(eventId: Long, request: ParticipantRequest): ParticipantResponse {
        val event = findEvent(eventId)
        val member = request.memberId?.let { memberRepository.findById(it).orElseThrow { notFound("member", it) } }
        val participant = participantRepository.save(
            Participant(
                event = event,
                member = member,
                name = member?.name ?: request.name?.trim().orEmpty(),
                grade = member?.grade ?: requireNotNull(request.grade).trim(),
                gender = member?.gender ?: normalizeGender(request.gender)
            )
        )
        clearGroups(eventId)
        return participant.toResponse()
    }

    @Transactional
    fun removeParticipant(eventId: Long, participantId: Long) {
        clearGroups(eventId)
        partnerPairRepository.deleteByEventIdAndParticipantId(eventId, participantId)
        completedCourtGameMemberRepository.deleteByEventIdAndParticipantId(eventId, participantId)
        val deleted = participantRepository.deleteParticipant(eventId, participantId)
        if (deleted == 0) throw notFound("participant", participantId)
    }

    @Transactional
    fun completeParticipants(eventId: Long, request: CompleteParticipantsRequest): EventDetailResponse {
        val event = findEvent(eventId)
        val requestedIds = request.participantIds.distinct()
        require(requestedIds.isNotEmpty()) { "completed participants are required" }
        val participants = participantRepository.findAllById(requestedIds)
        if (participants.size != requestedIds.size) throw ResponseStatusException(HttpStatus.NOT_FOUND, "participant not found")
        participants.forEach { participant ->
            require(participant.event.id == eventId) { "participant does not belong to event" }
            participant.gameCount += 1
        }
        val completedGame = completedCourtGameRepository.save(
            CompletedCourtGame(event = event, courtNo = request.courtNo, courtName = request.courtName?.ifBlank { null } ?: "${request.courtNo}코트")
        )
        completedCourtGameMemberRepository.saveAll(
            participants.mapIndexed { index, participant ->
                CompletedCourtGameMember(completedCourtGame = completedGame, participant = participant, seatNo = index + 1)
            }
        )
        participantRepository.saveAll(participants)
        return detail(eventId)
    }

    @Transactional
    fun addPartner(eventId: Long, request: PartnerRequest): PartnerResponse {
        val event = findEvent(eventId)
        val a = participantRepository.findById(request.participantAId).orElseThrow { notFound("participant", request.participantAId) }
        val b = participantRepository.findById(request.participantBId).orElseThrow { notFound("participant", request.participantBId) }
        require(a.event.id == eventId && b.event.id == eventId && a.id != b.id)
        val already = partnerPairRepository.findByEventId(eventId).flatMap { listOf(it.participantA.id, it.participantB.id) }.toSet()
        require(a.id !in already && b.id !in already)
        clearGroups(eventId)
        return partnerPairRepository.save(PartnerPair(event = event, participantA = a, participantB = b)).toResponse()
    }

    @Transactional
    fun removePartner(eventId: Long, partnerId: Long) {
        clearGroups(eventId)
        partnerPairRepository.deleteByEventIdAndId(eventId, partnerId)
    }

    @Transactional
    fun setComplete(eventId: Long, groupId: Long, request: CompleteRequest): GroupMatchResponse {
        val group = groupMatchRepository.findById(groupId).orElseThrow { notFound("group", groupId) }
        require(group.event.id == eventId)
        group.completed = request.completed
        return loadGroups(eventId).first { it.id == groupId }
    }

    fun loadGroups(eventId: Long): List<GroupMatchResponse> {
        val members = groupMatchMemberRepository.findByGroupMatchEventId(eventId).groupBy { it.groupMatch.id }
        return groupMatchRepository.findByEventIdOrderByRoundNoAscGroupNoAsc(eventId).map { group ->
            val groupMembers = members[group.id].orEmpty().sortedBy { it.seatNo }
            GroupMatchResponse(
                id = group.id,
                roundNo = group.roundNo,
                groupNo = group.groupNo,
                completed = group.completed,
                gradeSum = groupMembers.sumOf { com.dropshot.matching.domain.gradeScore(it.participant.grade) },
                members = groupMembers.map {
                    GroupMemberResponse(it.participant.id, it.participant.name, it.participant.grade)
                }
            )
        }
    }

    fun loadCompletedGames(eventId: Long): List<CompletedGameResponse> {
        val members = completedCourtGameMemberRepository
            .findByCompletedCourtGameEventIdOrderByCompletedCourtGameCompletedAtAscSeatNoAsc(eventId)
            .groupBy { it.completedCourtGame.id }
        return completedCourtGameRepository.findByEventIdOrderByCompletedAtAscIdAsc(eventId).map { completedGame ->
            CompletedGameResponse(
                id = completedGame.id,
                courtNo = completedGame.courtNo,
                courtName = completedGame.courtName,
                completedAt = completedGame.completedAt,
                members = members[completedGame.id].orEmpty().sortedBy { it.seatNo }.map {
                    CompletedGameMemberResponse(it.participant.id, it.participant.name, it.participant.grade)
                }
            )
        }
    }

    @Transactional
    fun clearGroups(eventId: Long) {
        groupMatchMemberRepository.deleteMembersByEventId(eventId)
        groupMatchRepository.deleteGroupsByEventId(eventId)
    }

    private fun findEvent(eventId: Long) =
        eventRepository.findById(eventId).orElseThrow { notFound("event", eventId) }

    private fun notFound(resource: String, id: Long) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "$resource not found: $id")

    private fun normalizeGender(value: String?) =
        when (value?.trim()) {
            "남", "남성" -> "남"
            "여", "여성" -> "여"
            else -> "미지정"
        }
}
