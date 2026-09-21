package com.rocayasociados.extensionbackend.users

import com.rocayasociados.extensionbackend.common.CurrentUserHolder
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class UsersController(
    private val currentUserHolder: CurrentUserHolder,
    private val usersService: UsersService,
) {

    @GetMapping("/me")
    fun me(): UserResponse = currentUserHolder.user.toResponse()

    @PatchMapping("/me")
    fun updateMe(@Valid @RequestBody request: UpdateProfileRequest): UserResponse =
        usersService.updateProfile(
            currentUserHolder.user.keycloakId,
            request.displayName,
            request.email,
        ).toResponse()
}
