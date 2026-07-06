package com.dropshot.matching.dto

import java.time.Instant

data class MemberResponse(val id: Long, val name: String, val grade: String, val gender: String)
data class MemberRequest(val name: String, val grade: String, val gender: String = "미지정")

data class EventRequest(val name: String = "DROP SHOT 모임", val gamesPerPlayer: Int = 10)
data class EventResponse(val id: Long, val name: String, val gamesPerPlayer: Int, val createdAt: Instant, val endedAt: Instant?)
data class EventStateRequest(
    val courtCount: Int,
    val gameStarted: Boolean,
    val courtAssignments: String = "{}",
    val courtNames: String = "{}",
    val waitingSince: String = "{}",
    val restingParticipantIds: String = "[]",
    val exitedParticipantIds: String = "[]"
)
data class EventStateResponse(
    val courtCount: Int,
    val gameStarted: Boolean,
    val courtAssignments: String,
    val courtNames: String,
    val waitingSince: String,
    val restingParticipantIds: String,
    val exitedParticipantIds: String
)

data class ParticipantRequest(val memberId: Long? = null, val name: String? = null, val grade: String? = null, val gender: String? = null)
data class ParticipantResponse(val id: Long, val memberId: Long?, val name: String, val grade: String, val gender: String, val gameCount: Int)
data class CompleteParticipantsRequest(val courtNo: Int, val courtName: String? = null, val participantIds: List<Long>)

data class PartnerRequest(val participantAId: Long, val participantBId: Long)
data class PartnerResponse(val id: Long, val participantAId: Long, val participantBId: Long)

data class CompletedGameMemberResponse(val participantId: Long, val name: String, val grade: String)
data class CompletedGameResponse(
    val id: Long,
    val courtNo: Int,
    val courtName: String,
    val completedAt: Instant,
    val members: List<CompletedGameMemberResponse>
)

data class GroupMemberResponse(val participantId: Long, val name: String, val grade: String)
data class GroupMatchResponse(
    val id: Long,
    val roundNo: Int,
    val groupNo: Int,
    val completed: Boolean,
    val gradeSum: Int,
    val members: List<GroupMemberResponse>
)

data class EventDetailResponse(
    val event: EventResponse,
    val participants: List<ParticipantResponse>,
    val partners: List<PartnerResponse>,
    val completedGames: List<CompletedGameResponse>,
    val state: EventStateResponse,
    val groups: List<GroupMatchResponse>
)

data class CompleteRequest(val completed: Boolean)
