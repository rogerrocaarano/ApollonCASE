import { useState } from "react"
import { toast } from "react-toastify"
import { useModalContext } from "@/contexts/ModalContext"
import { ProjectsApiClient } from "@/services/ExtensionApiClient"
import type { Project } from "@/types"
import { log } from "@/logger"
import {
  HomeDialogActions,
  HomeDialogContent,
  HomeDialogField,
  HomeDialogTextInput,
} from "./HomeDialog"

export interface RenameProjectModalProps {
  project: Project
  /** Called with the updated project so the caller can refresh its view. */
  onRenamed?: (project: Project) => void
}

export const RenameProjectModal = ({
  project,
  onRenamed,
}: RenameProjectModalProps) => {
  const { closeModal } = useModalContext()
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isValid = name.trim().length > 0

  const handleSave = async () => {
    if (!isValid || isSubmitting) return
    setIsSubmitting(true)
    try {
      const updated = await ProjectsApiClient.rename(project.id, {
        name: name.trim(),
        description: description.trim(),
      })
      onRenamed?.(updated)
      closeModal()
    } catch (err) {
      log.error("Failed to rename project", err as Error)
      toast.error("Could not rename the project. Please try again.")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <HomeDialogContent>
      <HomeDialogField label="Name" htmlFor="project-rename-name">
        <HomeDialogTextInput
          id="project-rename-name"
          autoFocus
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Enter project name"
        />
      </HomeDialogField>

      <HomeDialogField label="Description" htmlFor="project-rename-description">
        <HomeDialogTextInput
          id="project-rename-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Optional description"
        />
      </HomeDialogField>

      <HomeDialogActions
        cancelLabel="Cancel"
        confirmLabel="Save"
        loadingLabel="Saving…"
        loading={isSubmitting}
        confirmDisabled={!isValid}
        onCancel={closeModal}
        onConfirm={() => void handleSave()}
      />
    </HomeDialogContent>
  )
}
