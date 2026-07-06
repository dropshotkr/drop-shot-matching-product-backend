package com.dropshot.matching.domain

import jakarta.persistence.*
import java.time.Instant

fun gradeScore(grade: String) = when (grade) {
    "A조" -> 6
    "B조" -> 5
    "C조" -> 4
    "D조" -> 3
    "초심" -> 2
    "왕초심", "입문자" -> 1
    else -> 1
}

@Entity
@Table(
    schema = "dropshot",
    name = "members",
    uniqueConstraints = [UniqueConstraint(name = "uk_member_name_grade", columnNames = ["name", "grade"])]
)
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, name = "name")
    var name: String,
    @Column(nullable = false, length = 20, name = "grade")
    var grade: String,
    @Column(nullable = false, length = 10, name = "gender")
    var gender: String = "미지정",
    @Column(nullable = false, name = "created_at")
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(schema = "dropshot", name = "events")
class MatchingEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, name = "name")
    var name: String,
    @Column(nullable = false, name = "games_per_player")
    var gamesPerPlayer: Int = 10,
    @Column(nullable = false, name = "created_at")
    var createdAt: Instant = Instant.now(),
    @Column(name = "ended_at")
    var endedAt: Instant? = null,
    @Column(nullable = false, name = "court_count")
    var courtCount: Int = 4,
    @Column(nullable = false, name = "game_started")
    var gameStarted: Boolean = false,
    @Column(name = "court_assignments_json", columnDefinition = "TEXT")
    var courtAssignmentsJson: String? = "{}",
    @Column(name = "court_names_json", columnDefinition = "TEXT")
    var courtNamesJson: String? = "{}",
    @Column(name = "waiting_since_json", columnDefinition = "TEXT")
    var waitingSinceJson: String? = "{}",
    @Column(name = "resting_participant_ids_json", columnDefinition = "TEXT")
    var restingParticipantIdsJson: String? = "[]",
    @Column(name = "exited_participant_ids_json", columnDefinition = "TEXT")
    var exitedParticipantIdsJson: String? = "[]"
)

@Entity
@Table(schema = "dropshot", name = "participants")
class Participant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: MatchingEvent,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    var member: Member? = null,
    @Column(nullable = false, name = "name")
    var name: String,
    @Column(nullable = false, length = 20, name = "grade")
    var grade: String,
    @Column(nullable = false, length = 10, name = "gender")
    var gender: String = "미지정",
    @Column(nullable = false, name = "game_count")
    var gameCount: Int = 0
)

@Entity
@Table(schema = "dropshot", name = "partner_pairs")
class PartnerPair(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: MatchingEvent,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_a_id", nullable = false)
    var participantA: Participant,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_b_id", nullable = false)
    var participantB: Participant
)

@Entity
@Table(schema = "dropshot", name = "completed_court_games")
class CompletedCourtGame(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: MatchingEvent,
    @Column(nullable = false, name = "court_no")
    var courtNo: Int,
    @Column(nullable = false, name = "court_name")
    var courtName: String,
    @Column(nullable = false, name = "completed_at")
    var completedAt: Instant = Instant.now()
)

@Entity
@Table(schema = "dropshot", name = "completed_court_game_members")
class CompletedCourtGameMember(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "completed_court_game_id", nullable = false)
    var completedCourtGame: CompletedCourtGame,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,
    @Column(nullable = false, name = "seat_no")
    var seatNo: Int
)

@Entity
@Table(schema = "dropshot", name = "group_matches")
class GroupMatch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: MatchingEvent,
    @Column(nullable = false, name = "round_no")
    var roundNo: Int,
    @Column(nullable = false, name = "group_no")
    var groupNo: Int,
    @Column(nullable = false, name = "completed")
    var completed: Boolean = false
)

@Entity
@Table(schema = "dropshot", name = "group_match_members")
class GroupMatchMember(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_match_id", nullable = false)
    var groupMatch: GroupMatch,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,
    @Column(nullable = false, name = "seat_no")
    var seatNo: Int
)
