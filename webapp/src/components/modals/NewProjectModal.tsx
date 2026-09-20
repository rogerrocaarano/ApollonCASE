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

export interface NewProjectModalProps {
  /** Called with the created project so the caller can update its list. */
  onCreated?: (project: Project) => void
}

export const NewProjectModal = ({ onCreated }: NewProjectModalProps) => {
  const { closeModal } = useModalContext()
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isValid = name.trim().length > 0

  const handleCreate = async () => {
    if (!isValid || isSubmitting) return
    setIsSubmitting(true)
    try {
      const project = await ProjectsApiClient.create({
        name: name.trim(),
        description: description.trim(),
      })
      onCreated?.(project)
      closeModal()
    } catch (err) {
      log.error("Failed to create project", err as Error)
      toast.error("Could not create the project. Please try again.")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <HomeDialogContent>
      <HomeDialogField label="Name" htmlFor="project-name">
        <HomeDialogTextInput
          id="project-name"
          autoFocus
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Enter project name"
        />
      </HomeDialogField>

      <HomeDialogField label="Description" htmlFor="project-description">
        <HomeDialogTextInput
          id="project-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Optional description"
        />
      </HomeDialogField>

      <HomeDialogActions
        cancelLabel="Cancel"
        confirmLabel="Create Project"
        loadingLabel="Creating…"
        loading={isSubmitting}
        confirmDisabled={!isValid}
        onCancel={closeModal}
        onConfirm={() => void handleCreate()}
      />
    </HomeDialogContent>
  )
}
