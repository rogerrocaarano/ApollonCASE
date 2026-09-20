package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.common.requiredSubject
import com.rocayasociados.extensionbackend.users.UsersService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@Tag(name = "Projects", description = "Gestión de proyectos del usuario autenticado y sus diagramas asociados")
@RestController
@RequestMapping("/api/v1/projects")
class ProjectsController(
    private val projectsService: ProjectsService,
    private val usersService: UsersService,
) {

    @Operation(
        summary = "Lista los proyectos del usuario autenticado",
        description = "Solo devuelve los proyectos de los que el usuario autenticado es owner, ordenados por fecha de modificación descendente."
    )
    @GetMapping
    fun listProjects(
        @AuthenticationPrincipal jwt: Jwt,
    ): List<ProjectResponse> =
        projectsService.listOwnProjects(jwt.requiredSubject).map { it.toResponse() }

    @Operation(
        summary = "Obtiene un proyecto por id",
        description = "Solo el owner del proyecto puede consultarlo.",
        responses = [ApiResponse(responseCode = "403", description = "El usuario autenticado no es el owner del proyecto")]
    )
    @GetMapping("/{projectId}")
    fun getProject(
        @Parameter(description = "Id del proyecto") @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ProjectResponse =
        projectsService.getProject(projectId, jwt.requiredSubject).toResponse()

    @Operation(
        summary = "Lista los diagramas de un proyecto",
        description = "Solo el owner del proyecto puede consultarlos.",
        responses = [ApiResponse(responseCode = "403", description = "El usuario autenticado no es el owner del proyecto")]
    )
    @GetMapping("/{projectId}/diagrams")
    fun listProjectDiagrams(
        @Parameter(description = "Id del proyecto") @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<DiagramResponse> =
        projectsService.listProjectDiagrams(projectId, jwt.requiredSubject).map { it.toResponse() }

    @Operation(
        summary = "Crea un proyecto",
        description = "El usuario autenticado queda como owner del proyecto creado."
    )
    @PostMapping
    fun createProject(
        @Valid @RequestBody request: CreateProjectRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ProjectResponse> {
        val owner = usersService.trackKeycloakUser(jwt.requiredSubject)
        val project = projectsService.createProject(request.name, request.description, owner)
        return ResponseEntity.created(URI.create("/api/v1/projects/${project.id}")).body(project.toResponse())
    }

    @Operation(
        summary = "Actualiza parcialmente un proyecto",
        description = "Solo el owner puede modificarlo. Los campos omitidos en el body no se tocan.",
        responses = [ApiResponse(responseCode = "403", description = "El usuario autenticado no es el owner del proyecto")]
    )
    @PatchMapping("/{projectId}")
    fun updateProject(
        @Parameter(description = "Id del proyecto") @PathVariable projectId: UUID,
        @RequestBody request: UpdateProjectRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ProjectResponse {
        val subject = jwt.requiredSubject
        request.name?.let { projectsService.changeProjectName(projectId, it, subject) }
        request.description?.let { projectsService.changeProjectDescription(projectId, it, subject) }
        return projectsService.getProject(projectId, subject).toResponse()
    }

    @Operation(
        summary = "Asocia un diagrama existente (en Redis) a un proyecto",
        description = "Solo el owner del proyecto puede asociar diagramas.",
        responses = [ApiResponse(responseCode = "403", description = "El usuario autenticado no es el owner del proyecto")]
    )
    @PostMapping("/{projectId}/diagrams")
    @ResponseStatus(HttpStatus.CREATED)
    fun linkDiagram(
        @Parameter(description = "Id del proyecto") @PathVariable projectId: UUID,
        @RequestBody request: LinkDiagramRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): DiagramResponse =
        projectsService.linkDiagramToProject(projectId, request.redisId, jwt.requiredSubject).toResponse()

    @Operation(
        summary = "Elimina un diagrama de un proyecto",
        description = "Borra el diagrama en extension-backend y su cuerpo en diagrams-backend. Solo el owner del proyecto puede hacerlo.",
        responses = [ApiResponse(responseCode = "403", description = "El usuario autenticado no es el owner del proyecto")]
    )
    @DeleteMapping("/{projectId}/diagrams/{diagramId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDiagram(
        @Parameter(description = "Id del proyecto") @PathVariable projectId: UUID,
        @Parameter(description = "Id del diagrama") @PathVariable diagramId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        projectsService.deleteDiagram(projectId, diagramId, jwt.requiredSubject)
    }

    @Operation(
        summary = "Elimina un proyecto",
        description = "Borra también todos sus diagramas, en extension-backend y en diagrams-backend. Solo el owner puede hacerlo.",
        responses = [ApiResponse(responseCode = "403", description = "El usuario autenticado no es el owner del proyecto")]
    )
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProject(
        @Parameter(description = "Id del proyecto") @PathVariable projectId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        projectsService.deleteProject(projectId, jwt.requiredSubject)
    }
}
