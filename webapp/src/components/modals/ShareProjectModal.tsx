import { useState } from "react"
import { toast } from "react-toastify"
import { useModalContext } from "@/contexts/ModalContext"
import {
  ExtensionApiError,
  ProjectsApiClient,
} from "@/services/ExtensionApiClient"
import { log } from "@/logger"
import {
  HomeDialogActions,
  HomeDialogContent,
  HomeDialogField,
  HomeDialogOptionGroup,
  HomeDialogTextInput,
  type HomeDialogOption,
} from "./HomeDialog"

type ShareableRole = "COLLABORATOR" | "VIEWER"

const ROLE_OPTIONS: readonly HomeDialogOption<ShareableRole>[] = [
  { value: "COLLABORATOR", label: "Collaborator" },
  { value: "VIEWER", label: "Viewer" },
]

export interface ShareProjectModalProps {
  projectId: string
}

/**
 * Owner-only "invite by email" form. There is no member-list endpoint yet
 * (see `share-project-by-email` proposal - Out of scope), so this can only
 * grant/update a role — it has no roster to show.
 */
export const ShareProjectModal = ({ projectId }: ShareProjectModalProps) => {
  const { closeModal } = useModalContext()
  const [email, setEmail] = useState("")
  const [role, setRole] = useState<ShareableRole>("COLLABORATOR")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isValid = email.trim().length > 0

  const handleShare = async () => {
    if (!isValid || isSubmitting) return
    setIsSubmitting(true)
    setError(null)
    try {
      await ProjectsApiClient.shareProject(projectId, {
        email: email.trim(),
        role,
      })
      toast.success(`Shared with ${email.trim()}`)
      closeModal()
    } catch (err) {
      log.error("Failed to share project", err as Error)
      if (err instanceof ExtensionApiError && err.status === 404) {
        setError(
          "No user found with that email — they need to have signed in at least once."
        )
      } else {
        setError("Could not share the project. Please try again.")
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <HomeDialogContent>
      <HomeDialogField label="Email" htmlFor="share-project-email">
        <HomeDialogTextInput
          id="share-project-email"
          type="email"
          autoFocus
          value={email}
          onChange={(event) => {
            setEmail(event.target.value)
            setError(null)
          }}
          placeholder="Enter an email address"
        />
      </HomeDialogField>

      <HomeDialogOptionGroup
        label="Role"
        options={ROLE_OPTIONS}
        value={role}
        onChange={setRole}
        columns={2}
      />

      {error ? (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}

      <HomeDialogActions
        cancelLabel="Cancel"
        confirmLabel="Share"
        loadingLabel="Sharing…"
        loading={isSubmitting}
        confirmDisabled={!isValid}
        onCancel={closeModal}
        onConfirm={() => void handleShare()}
      />
    </HomeDialogContent>
  )
}
