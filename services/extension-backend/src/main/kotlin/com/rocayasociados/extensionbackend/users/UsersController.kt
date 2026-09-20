package com.rocayasociados.extensionbackend.users

import com.rocayasociados.extensionbackend.common.CurrentUserHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class UsersController(
    private val currentUserHolder: CurrentUserHolder,
) {

    @GetMapping("/me")
    fun me(): UserResponse = currentUserHolder.user.toResponse()
}
