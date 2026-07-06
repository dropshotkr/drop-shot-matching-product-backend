package com.dropshot.matching.controller

import com.dropshot.matching.dto.MemberRequest
import com.dropshot.matching.dto.MemberResponse
import com.dropshot.matching.service.MemberService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {
    @GetMapping
    fun search(@RequestParam(required = false) q: String?): List<MemberResponse> = memberService.search(q)

    @PostMapping
    fun create(@RequestBody request: MemberRequest): MemberResponse = memberService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: MemberRequest): MemberResponse =
        memberService.update(id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = memberService.delete(id)
}
