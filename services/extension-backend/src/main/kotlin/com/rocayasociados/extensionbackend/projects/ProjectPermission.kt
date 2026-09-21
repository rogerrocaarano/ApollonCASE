package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "project_permissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "user_id"])],
)
class ProjectPermission(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var permission: ProjectPermissionType,
)

/**
 * Declared least-privileged first so `compareTo` expresses the access
 * hierarchy directly: `OWNER` includes every `COLLABORATOR` action, and
 * `COLLABORATOR` includes every `VIEWER` action.
 */
enum class ProjectPermissionType {
    VIEWER, COLLABORATOR, OWNER
}
