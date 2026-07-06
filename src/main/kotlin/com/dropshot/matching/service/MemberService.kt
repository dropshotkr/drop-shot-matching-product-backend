package com.dropshot.matching.service

import com.dropshot.matching.domain.Member
import com.dropshot.matching.dto.MemberRequest
import com.dropshot.matching.dto.MemberResponse
import com.dropshot.matching.repository.MemberRepository
import com.dropshot.matching.repository.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val participantRepository: ParticipantRepository
) {
    @Transactional(readOnly = true)
    fun search(query: String?): List<MemberResponse> {
        val members = if (query.isNullOrBlank()) {
            memberRepository.findAll().sortedBy { it.name }
        } else {
            memberRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim())
        }
        return members.map { it.toResponse() }
    }

    @Transactional
    fun create(request: MemberRequest): MemberResponse {
        val member = memberRepository.save(
            Member(
                name = request.name.trim(),
                grade = request.grade.trim(),
                gender = normalizeGender(request.gender)
            )
        )
        return member.toResponse()
    }

    @Transactional
    fun update(id: Long, request: MemberRequest): MemberResponse {
        val member = memberRepository.findById(id).orElseThrow()
        val nextName = request.name.trim()
        val nextGrade = request.grade.trim()
        val nextGender = normalizeGender(request.gender)
        member.name = nextName
        member.grade = nextGrade
        member.gender = nextGender
        participantRepository.findByMemberId(id).forEach { participant ->
            participant.name = nextName
            participant.grade = nextGrade
            participant.gender = nextGender
        }
        return member.toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        memberRepository.deleteById(id)
    }

    private fun normalizeGender(value: String?) =
        when (value?.trim()) {
            "남", "남성" -> "남"
            "여", "여성" -> "여"
            else -> "미지정"
        }
}
