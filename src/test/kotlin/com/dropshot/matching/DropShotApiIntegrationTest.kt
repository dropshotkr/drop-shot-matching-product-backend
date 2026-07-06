package com.dropshot.matching

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DropShotApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:dropshot_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
            }
            registry.add("spring.datasource.driver-class-name") { "org.h2.Driver" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.sql.init.mode") { "always" }
            registry.add("spring.sql.init.schema-locations") { "classpath:schema.sql" }
        }
    }

    @Test
    fun `missing event returns 404 instead of NoSuchElementException`() {
        mockMvc.perform(get("/api/events/999999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `admin can create search update and delete member with visible grade and gender`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val created = postJson("/api/members", """{"name":"테스트회원-$suffix","grade":"왕초심","gender":"남"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.grade").value("왕초심"))
            .andExpect(jsonPath("$.gender").value("남"))
            .andReturnJson()

        val memberId = created["id"].asLong()

        mockMvc.perform(get("/api/members").param("q", "테스트회원-$suffix"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].grade").value("왕초심"))
            .andExpect(jsonPath("$[0].gender").value("남"))

        putJson("/api/members/$memberId", """{"name":"테스트회원수정-$suffix","grade":"초심","gender":"여"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("테스트회원수정-$suffix"))
            .andExpect(jsonPath("$.grade").value("초심"))
            .andExpect(jsonPath("$.gender").value("여"))

        mockMvc.perform(delete("/api/members/$memberId"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/members").param("q", "테스트회원수정-$suffix"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
    }

    @Test
    fun `member gender update is reflected to registered participants`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val member = postJson("/api/members", """{"name":"성별수정-$suffix","grade":"왕초심","gender":"미지정"}""")
            .andExpect(status().isOk)
            .andReturnJson()
        val memberId = member["id"].asLong()
        val eventId = postJson("/api/events", """{"name":"성별 테스트","gamesPerPlayer":10}""")
            .andExpect(status().isOk)
            .andReturnJson()["id"].asLong()

        postJson("/api/events/$eventId/participants", """{"memberId":$memberId}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gender").value("미지정"))

        putJson("/api/members/$memberId", """{"name":"성별수정-$suffix","grade":"왕초심","gender":"여"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gender").value("여"))

        mockMvc.perform(get("/api/events/$eventId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants[0].gender").value("여"))
    }

    @Test
    fun `event flow registers participants creates partners generates groups and marks complete`() {
        val event = postJson("/api/events", """{"name":"테스트 모임","gamesPerPlayer":10}""")
            .andExpect(status().isOk)
            .andReturnJson()
        val eventId = event["id"].asLong()

        val memberIds = listOf(
            createMember("테스트A", "A조"),
            createMember("테스트B", "B조"),
            createMember("테스트C", "C조"),
            createMember("테스트D", "D조"),
            createMember("테스트E", "초심"),
            createMember("테스트F1", "왕초심"),
            createMember("테스트F2", "입문자"),
            createMember("테스트F3", "왕초심")
        )
        val participantIds = memberIds.map { memberId ->
            postJson("/api/events/$eventId/participants", """{"memberId":$memberId}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.gameCount").value(0))
                .andReturnJson()["id"].asLong()
        }

        putJson(
            "/api/events/$eventId/state",
            """{"courtCount":3,"gameStarted":true,"courtAssignments":"{\"1\":[${participantIds[0]},${participantIds[1]}]}","courtNames":"{\"1\":\"메인 코트\"}","waitingSince":"{\"${participantIds[2]}\":1710000000000}"}"""
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.courtCount").value(3))
            .andExpect(jsonPath("$.gameStarted").value(true))

        postJson(
            "/api/events/$eventId/partners",
            """{"participantAId":${participantIds[0]},"participantBId":${participantIds[1]}}"""
        )
            .andExpect(status().isOk)

        val groups = postJson("/api/events/$eventId/groups/generate", "")
            .andExpect(status().isOk)
            .andReturnJson()

        assertTrue(groups.size() > 0)
        assertEquals(4, groups[0]["members"].size())

        val groupId = groups[0]["id"].asLong()
        patchJson("/api/events/$eventId/groups/$groupId/complete", """{"completed":true}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.completed").value(true))

        postJson(
            "/api/events/$eventId/participants/complete-games",
            """{"courtNo":1,"courtName":"메인 코트","participantIds":[${participantIds.take(4).joinToString(",")}]}"""
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants[0].gameCount").value(1))
            .andExpect(jsonPath("$.completedGames", hasSize<Any>(1)))
            .andExpect(jsonPath("$.completedGames[0].courtNo").value(1))
            .andExpect(jsonPath("$.completedGames[0].courtName").value("메인 코트"))
            .andExpect(jsonPath("$.completedGames[0].members", hasSize<Any>(4)))

        postJson("/api/events/$eventId/finish", "")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.endedAt").exists())

        mockMvc.perform(get("/api/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].endedAt").exists())

        mockMvc.perform(get("/api/events/$eventId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants", hasSize<Any>(8)))
            .andExpect(jsonPath("$.partners", hasSize<Any>(1)))
            .andExpect(jsonPath("$.state.courtCount").value(3))
            .andExpect(jsonPath("$.state.gameStarted").value(false))
            .andExpect(jsonPath("$.state.courtNames").value("{\"1\":\"메인 코트\"}"))
            .andExpect(jsonPath("$.groups[0].completed").value(true))
            .andExpect(jsonPath("$.participants[0].gameCount").value(1))
            .andExpect(jsonPath("$.completedGames", hasSize<Any>(1)))

        mockMvc.perform(delete("/api/events/$eventId/participants/${participantIds[0]}"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/events/$eventId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants", hasSize<Any>(7)))
            .andExpect(jsonPath("$.partners", hasSize<Any>(0)))
            .andExpect(jsonPath("$.groups", hasSize<Any>(0)))
    }

    private fun createMember(namePrefix: String, grade: String): Long {
        val name = "$namePrefix-${UUID.randomUUID().toString().take(8)}"
        return postJson("/api/members", """{"name":"$name","grade":"$grade"}""")
            .andExpect(status().isOk)
            .andReturnJson()["id"].asLong()
    }

    private fun postJson(path: String, content: String) =
        mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content.ifBlank { "{}" })
        )

    private fun putJson(path: String, content: String) =
        mockMvc.perform(
            put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
        )

    private fun patchJson(path: String, content: String) =
        mockMvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
        )

    private fun org.springframework.test.web.servlet.ResultActions.andReturnJson(): JsonNode =
        objectMapper.readTree(andReturn().response.contentAsString)
}
