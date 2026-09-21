package com.rocayasociados.extensionbackend.users

import com.rocayasociados.extensionbackend.projects.Project
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.UUID


@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true)
    var keycloakId: String,

    @Column
    var displayName: String? = null,

    @Column
    var email: String? = null,

    @OneToMany(
        mappedBy = "owner",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var projects: MutableList<Project> = mutableListOf(),
)