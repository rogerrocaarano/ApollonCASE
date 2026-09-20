package com.rocayasociados.extensionbackend.common

import com.rocayasociados.extensionbackend.users.User
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

@Component
@RequestScope
class CurrentUserHolder {
    lateinit var user: User
}
