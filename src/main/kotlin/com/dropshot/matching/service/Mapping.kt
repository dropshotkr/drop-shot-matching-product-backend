package com.dropshot.matching.service

import com.dropshot.matching.domain.*
import com.dropshot.matching.dto.*

fun Member.toResponse() = MemberResponse(id = id, name = name, grade = grade, gender = gender)

fun MatchingEvent.toResponse() = EventResponse(
    id = id,
    name = name,
    gamesPerPlayer = gamesPerPlayer,
    createdAt = createdAt,
    endedAt = endedAt
)

fun MatchingEvent.toStateResponse() = EventStateResponse(
    courtCount = courtCount,
    gameStarted = gameStarted,
    courtAssignments = courtAssignmentsJson ?: "{}",
    courtNames = courtNamesJson ?: "{}",
    waitingSince = waitingSinceJson ?: "{}",
    restingParticipantIds = restingParticipantIdsJson ?: "[]",
    exitedParticipantIds = exitedParticipantIdsJson ?: "[]"
)

fun Participant.toResponse() = ParticipantResponse(
    id = id,
    memberId = member?.id,
    name = name,
    grade = grade,
    gender = gender,
    gameCount = gameCount
)

fun PartnerPair.toResponse() = PartnerResponse(
    id = id,
    participantAId = participantA.id,
    participantBId = participantB.id
)
