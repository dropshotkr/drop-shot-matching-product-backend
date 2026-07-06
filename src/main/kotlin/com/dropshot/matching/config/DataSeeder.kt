package com.dropshot.matching.config

import com.dropshot.matching.domain.Member
import com.dropshot.matching.repository.MemberRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class DataSeeder(
    private val memberRepository: MemberRepository
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        if (memberRepository.count() > 0) return

        val resource = ClassPathResource("drop-shot-players.csv")
        resource.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                val cells = line.split(",").map { it.trim().trim('"') }
                val name = cells.getOrNull(0).orEmpty()
                val grade = cells.getOrNull(1).orEmpty()
                if (name.isNotBlank() && grade.isNotBlank() && !memberRepository.existsByNameAndGrade(name, grade)) {
                    memberRepository.save(Member(name = name, grade = grade))
                }
            }
        }
    }
}
