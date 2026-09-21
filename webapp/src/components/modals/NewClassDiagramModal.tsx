import { useState } from "react"
import { toast } from "react-toastify"
import { useModalContext } from "@/contexts/ModalContext"
import { UMLDiagramType, type UMLModel } from "@tumaet/apollon"
import { useNavigate } from "@tanstack/react-router"
import { usePersistenceModelStore } from "@/stores/usePersistenceModelStore"
import { getDiagramTypeIcon } from "@/components/home/diagramTypeMeta"
import { log } from "@/logger"
import { ProjectsApiClient } from "@/services/ExtensionApiClient"
import {
  HomeDialogActions,
  HomeDialogContent,
  HomeDialogField,
  HomeDialogTextInput,
} from "./HomeDialog"

export interface NewClassDiagramModalProps {
  /**
   * When set, the modal was opened from a project: creation goes through
   * `diagrams-backend` + `extension-backend`'s link endpoint and lands in the
   * collaborative editor, instead of the local (IndexedDB-only) store. Mirrors
   * `NewDiagramModal`'s `projectId` prop — see its doc comment and
   * design.md's "New diagram creation flow" decision in `add-projects`.
   */
  projectId?: string
}

export const NewClassDiagramModal = ({
  projectId,
}: NewClassDiagramModalProps) => {
  const { closeModal } = useModalContext()
  const [name, setName] = useState("")
  const [isCreating, setIsCreating] = useState(false)
  const navigate = useNavigate()
  const createModelByTitleAndType = usePersistenceModelStore(
    (state) => state.createModelByTitleAndType
  )

  const isValid = name.trim().length > 0

  // extension-backend creates the diagram body in diagrams-backend
  // server-side and links it to the project in one call — the browser never
  // sees the diagrams-backend id (see gate-project-diagrams). Then opens it
  // in the project-diagram collaborative editor.
  const createDiagramInProject = async (model: UMLModel) => {
    if (!projectId) return
    setIsCreating(true)
    try {
      const created = await ProjectsApiClient.createDiagram(projectId, model)
      closeModal()
      navigate({
        to: "/projects/$projectId/diagrams/$diagramId",
        params: { projectId, diagramId: created.id },
      })
    } catch (err) {
      log.error("Failed to create diagram in project", err as Error)
      toast.error("Could not create the diagram. Please try again.")
    } finally {
      setIsCreating(false)
    }
  }

  const handleCreate = () => {
    if (!isValid || isCreating) return

    if (projectId) {
      void createDiagramInProject({
        id: crypto.randomUUID(),
        type: UMLDiagramType.ClassDiagram,
        assessments: {},
        edges: [],
        nodes: [],
        title: name,
        version: "4.0.0",
      })
      return
    }

    const newId = createModelByTitleAndType(name, UMLDiagramType.ClassDiagram)
    closeModal()
    navigate({ to: "/local/$id", params: { id: newId } })
  }

  return (
    <HomeDialogContent>
      <HomeDialogField label="Name" htmlFor="diagram-title">
        <div className="flex items-center gap-3">
          <div className="text-muted-foreground" aria-hidden="true">
            {getDiagramTypeIcon(UMLDiagramType.ClassDiagram, "h-9 w-9")}
          </div>
          <HomeDialogTextInput
            id="diagram-title"
            autoFocus
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Enter diagram name"
            className="flex-1"
          />
        </div>
      </HomeDialogField>

      <HomeDialogActions
        cancelLabel="Cancel"
        confirmLabel="Create Diagram"
        loadingLabel="Creating…"
        loading={isCreating}
        confirmDisabled={!isValid}
        onCancel={closeModal}
        onConfirm={handleCreate}
      />
    </HomeDialogContent>
  )
}
