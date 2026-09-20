package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.User
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "projects")
class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var description: String,


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,

    @OneToMany(
        mappedBy = "project",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var diagrams: MutableList<Diagram> = mutableListOf(),

    // `columnDefinition` gives `ddl-auto=update` a SQL-level DEFAULT so adding
    // this NOT NULL column doesn't fail against the existing (pre-this-change)
    // rows in the shared dev "projects" table.
    @Column(nullable = false, columnDefinition = "timestamptz default now()")
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false, columnDefinition = "timestamptz default now()")
    var updatedAt: Instant = Instant.now(),
) {
}