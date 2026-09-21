package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.UsersService
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs against the real dev datasource (application-local.properties), like
 * the rest of this project's tests. @Transactional rolls back every write at
 * the end of each test so the shared dev database is left untouched.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ProjectPermissionRepositoryTest {

    @Autowired
    lateinit var usersService: UsersService

    @Autowired
    lateinit var projectsRepository: ProjectsRepository

    @Autowired
    lateinit var projectPermissionRepository: ProjectPermissionRepository

    @PersistenceContext
    lateinit var entityManager: EntityManager

    private fun newSubject() = "test-subject-${UUID.randomUUID()}"

    @Test
    fun `schema no tiene owner_id en projects y si tiene project_permissions`() {
        @Suppress("UNCHECKED_CAST")
        val projectColumns = entityManager.createNativeQuery(
            "select column_name from information_schema.columns where table_name = 'projects'"
        ).resultList as List<String>
        assertFalse(projectColumns.contains("owner_id"), "owner_id no debe existir tras la migracion")

        @Suppress("UNCHECKED_CAST")
        val permissionTables = entityManager.createNativeQuery(
            "select table_name from information_schema.tables where table_name = 'project_permissions'"
        ).resultList as List<String>
        assertTrue(permissionTables.isNotEmpty(), "project_permissions debe existir")
    }

    @Test
    fun `findByProject_IdAndUser_Id encuentra la fila exacta`() {
        val user = usersService.trackKeycloakUser(newSubject())
        val project = projectsRepository.save(Project(name = "P", description = "d"))
        projectPermissionRepository.save(
            ProjectPermission(project = project, user = user, permission = ProjectPermissionType.VIEWER)
        )

        val found = projectPermissionRepository.findByProject_IdAndUser_Id(
            requireNotNull(project.id),
            requireNotNull(user.id),
        )
        assertEquals(ProjectPermissionType.VIEWER, found?.permission)
    }

    @Test
    fun `findByProject_IdAndUser_Id sin fila devuelve null`() {
        val user = usersService.trackKeycloakUser(newSubject())
        val project = projectsRepository.save(Project(name = "P", description = "d"))

        assertNull(
            projectPermissionRepository.findByProject_IdAndUser_Id(
                requireNotNull(project.id),
                requireNotNull(user.id),
            )
        )
    }

    @Test
    fun `findAllByUser_KeycloakId devuelve todas las filas del usuario across proyectos`() {
        val subject = newSubject()
        val user = usersService.trackKeycloakUser(subject)
        val projectA = projectsRepository.save(Project(name = "A", description = "d"))
        val projectB = projectsRepository.save(Project(name = "B", description = "d"))
        projectPermissionRepository.save(
            ProjectPermission(project = projectA, user = user, permission = ProjectPermissionType.OWNER)
        )
        projectPermissionRepository.save(
            ProjectPermission(project = projectB, user = user, permission = ProjectPermissionType.VIEWER)
        )

        val permissions = projectPermissionRepository.findAllByUser_KeycloakId(subject)
        assertEquals(2, permissions.size)
        assertEquals(
            setOf(ProjectPermissionType.OWNER, ProjectPermissionType.VIEWER),
            permissions.map { it.permission }.toSet(),
        )
    }

    @Test
    fun `findByProject_IdAndPermission resuelve el OWNER de un proyecto`() {
        val owner = usersService.trackKeycloakUser(newSubject())
        val project = projectsRepository.save(Project(name = "P", description = "d"))
        projectPermissionRepository.save(
            ProjectPermission(project = project, user = owner, permission = ProjectPermissionType.OWNER)
        )

        val found = projectPermissionRepository.findByProject_IdAndPermission(
            requireNotNull(project.id),
            ProjectPermissionType.OWNER,
        )
        assertEquals(owner.id, found?.user?.id)
    }

    @Test
    fun `unique constraint impide dos filas de permiso para el mismo usuario y proyecto`() {
        val user = usersService.trackKeycloakUser(newSubject())
        val project = projectsRepository.save(Project(name = "P", description = "d"))
        projectPermissionRepository.saveAndFlush(
            ProjectPermission(project = project, user = user, permission = ProjectPermissionType.VIEWER)
        )

        assertFailsWith<DataIntegrityViolationException> {
            projectPermissionRepository.saveAndFlush(
                ProjectPermission(project = project, user = user, permission = ProjectPermissionType.COLLABORATOR)
            )
        }
    }
}
