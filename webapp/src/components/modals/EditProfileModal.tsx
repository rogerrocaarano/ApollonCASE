import { useState } from "react"
import { toast } from "react-toastify"
import { useModalContext } from "@/contexts/ModalContext"
import { ExtensionApiClient, type CurrentUser } from "@/services/ExtensionApiClient"
import { log } from "@/logger"
import {
  HomeDialogActions,
  HomeDialogContent,
  HomeDialogField,
  HomeDialogTextInput,
} from "./HomeDialog"

export interface EditProfileModalProps {
  user: CurrentUser
  /** Called with the updated user so the caller can refresh its view. */
  onUpdated?: (user: CurrentUser) => void
}

export const EditProfileModal = ({ user, onUpdated }: EditProfileModalProps) => {
  const { closeModal } = useModalContext()
  const [displayName, setDisplayName] = useState(user.displayName ?? "")
  const [email, setEmail] = useState(user.email ?? "")
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSave = async () => {
    if (isSubmitting) return
    setIsSubmitting(true)
    try {
      const updated = await ExtensionApiClient.updateMe({
        displayName: displayName.trim(),
        email: email.trim(),
      })
      onUpdated?.(updated)
      closeModal()
    } catch (err) {
      log.error("Failed to update profile", err as Error)
      toast.error("Could not update your profile. Please try again.")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <HomeDialogContent>
      <HomeDialogField label="Display name" htmlFor="profile-display-name">
        <HomeDialogTextInput
          id="profile-display-name"
          autoFocus
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          placeholder="Enter a display name"
        />
      </HomeDialogField>

      <HomeDialogField label="Email" htmlFor="profile-email">
        <HomeDialogTextInput
          id="profile-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="Enter an email address"
        />
      </HomeDialogField>

      <HomeDialogActions
        cancelLabel="Cancel"
        confirmLabel="Save"
        loadingLabel="Saving…"
        loading={isSubmitting}
        onCancel={closeModal}
        onConfirm={() => void handleSave()}
      />
    </HomeDialogContent>
  )
}
