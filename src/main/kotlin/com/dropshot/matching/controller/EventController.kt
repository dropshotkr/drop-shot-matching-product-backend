package com.dropshot.matching.controller

import com.dropshot.matching.dto.*
import com.dropshot.matching.service.EventService
import com.dropshot.matching.service.MatchingService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService,
    private val matchingService: MatchingService
) {
    @PostMapping
    fun create(@RequestBody request: EventRequest): EventResponse = eventService.create(request)

    @GetMapping
    fun list(): List<EventResponse> = eventService.list()

    @GetMapping("/{eventId}")
    fun detail(@PathVariable eventId: Long): EventDetailResponse = eventService.detail(eventId)

    @PutMapping("/{eventId}/state")
    fun updateState(@PathVariable eventId: Long, @RequestBody request: EventStateRequest): EventStateResponse =
        eventService.updateState(eventId, request)

    @PostMapping("/{eventId}/finish")
    fun finish(@PathVariable eventId: Long): EventResponse = eventService.finish(eventId)

    @PostMapping("/{eventId}/participants")
    fun addParticipant(
        @PathVariable eventId: Long,
        @RequestBody request: ParticipantRequest
    ): ParticipantResponse = eventService.addParticipant(eventId, request)

    @DeleteMapping("/{eventId}/participants/{participantId}")
    fun removeParticipant(@PathVariable eventId: Long, @PathVariable participantId: Long) =
        eventService.removeParticipant(eventId, participantId)

    @PostMapping("/{eventId}/participants/complete-games")
    fun completeParticipants(
        @PathVariable eventId: Long,
        @RequestBody request: CompleteParticipantsRequest
    ): EventDetailResponse = eventService.completeParticipants(eventId, request)

    @PostMapping("/{eventId}/partners")
    fun addPartner(@PathVariable eventId: Long, @RequestBody request: PartnerRequest): PartnerResponse =
        eventService.addPartner(eventId, request)

    @DeleteMapping("/{eventId}/partners/{partnerId}")
    fun removePartner(@PathVariable eventId: Long, @PathVariable partnerId: Long) =
        eventService.removePartner(eventId, partnerId)

    @PostMapping("/{eventId}/groups/generate")
    fun generate(@PathVariable eventId: Long): List<GroupMatchResponse> = matchingService.generate(eventId)

    @PostMapping("/{eventId}/groups/replan")
    fun replan(@PathVariable eventId: Long): List<GroupMatchResponse> = matchingService.replan(eventId)

    @PatchMapping("/{eventId}/groups/{groupId}/complete")
    fun setComplete(
        @PathVariable eventId: Long,
        @PathVariable groupId: Long,
        @RequestBody request: CompleteRequest
    ): GroupMatchResponse = eventService.setComplete(eventId, groupId, request)
}
