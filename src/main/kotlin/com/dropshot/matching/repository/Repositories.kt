package com.dropshot.matching.repository

import com.dropshot.matching.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByNameContainingIgnoreCaseOrderByNameAsc(name: String): List<Member>
    fun existsByNameAndGrade(name: String, grade: String): Boolean
}

interface EventRepository : JpaRepository<MatchingEvent, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<MatchingEvent>
}

interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun findByEventIdOrderByNameAsc(eventId: Long): List<Participant>
    fun findByMemberId(memberId: Long): List<Participant>

    @Modifying
    @Query("delete from Participant p where p.event.id = :eventId and p.id = :participantId")
    fun deleteParticipant(eventId: Long, participantId: Long): Int
}

interface PartnerPairRepository : JpaRepository<PartnerPair, Long> {
    fun findByEventId(eventId: Long): List<PartnerPair>
    fun deleteByEventIdAndId(eventId: Long, id: Long)
    fun deleteByEventId(eventId: Long)

    @Modifying
    @Query("delete from PartnerPair p where p.event.id = :eventId and (p.participantA.id = :participantId or p.participantB.id = :participantId)")
    fun deleteByEventIdAndParticipantId(eventId: Long, participantId: Long): Int
}

interface CompletedCourtGameRepository : JpaRepository<CompletedCourtGame, Long> {
    fun findByEventIdOrderByCompletedAtAscIdAsc(eventId: Long): List<CompletedCourtGame>
}

interface CompletedCourtGameMemberRepository : JpaRepository<CompletedCourtGameMember, Long> {
    fun findByCompletedCourtGameEventIdOrderByCompletedCourtGameCompletedAtAscSeatNoAsc(eventId: Long): List<CompletedCourtGameMember>

    @Modifying
    @Query("delete from CompletedCourtGameMember m where m.completedCourtGame.event.id = :eventId and m.participant.id = :participantId")
    fun deleteByEventIdAndParticipantId(eventId: Long, participantId: Long): Int
}

interface GroupMatchRepository : JpaRepository<GroupMatch, Long> {
    fun findByEventIdOrderByRoundNoAscGroupNoAsc(eventId: Long): List<GroupMatch>

    @Modifying
    @Query("delete from GroupMatch g where g.event.id = :eventId")
    fun deleteGroupsByEventId(eventId: Long): Int

    @Modifying
    @Query("delete from GroupMatch g where g.event.id = :eventId and g.roundNo >= :roundNo")
    fun deleteGroupsByEventIdAndRoundNoGreaterThanEqual(eventId: Long, roundNo: Int): Int
}

interface GroupMatchMemberRepository : JpaRepository<GroupMatchMember, Long> {
    fun findByGroupMatchEventId(eventId: Long): List<GroupMatchMember>

    @Modifying
    @Query("delete from GroupMatchMember m where m.groupMatch.event.id = :eventId")
    fun deleteMembersByEventId(eventId: Long): Int

    @Modifying
    @Query("delete from GroupMatchMember m where m.groupMatch.event.id = :eventId and m.groupMatch.roundNo >= :roundNo")
    fun deleteMembersByEventIdAndRoundNoGreaterThanEqual(eventId: Long, roundNo: Int): Int
}
