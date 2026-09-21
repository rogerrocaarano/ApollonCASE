import type { Meta, StoryObj } from "@storybook/react-vite"
import { expect, userEvent, within } from "storybook/test"
import { withModalFrame } from "../../stories/_support/webapp"
import { ShareProjectModal } from "./ShareProjectModal"

/**
 * Owner-only "share by email" form. There is no member-list endpoint (see
 * `share-project-by-email` proposal - Out of scope), so this can only
 * grant/update a role — these stories render the form without submitting it,
 * the same convention `ShareDashboardModal`'s stories follow to avoid
 * triggering a real network call.
 */

const meta = {
  title: "Webapp/Modals/ShareProjectModal",
  component: ShareProjectModal,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: { story: { inline: false, height: "420px" } },
  },
  decorators: [
    withModalFrame({
      title: "Share project",
      variant: "home-compact",
    }),
  ],
  args: { projectId: "p1" },
} satisfies Meta<typeof ShareProjectModal>

export default meta
type Story = StoryObj<typeof meta>

/** Initial form: email empty, Collaborator selected by default, Share disabled. */
export const Default: Story = {
  play: async () => {
    const canvas = within(document.body)
    const shareButton = canvas.getByRole("button", { name: "Share" })
    await expect(shareButton).toBeDisabled()
    await expect(
      canvas.getByRole("button", { name: "Collaborator" })
    ).toHaveAttribute("aria-pressed", "true")
  },
}

/** Picking Viewer switches the selected role and enables Share once an email is entered. */
export const ViewerRoleSelected: Story = {
  play: async () => {
    const canvas = within(document.body)

    await userEvent.click(canvas.getByRole("button", { name: "Viewer" }))
    await expect(
      canvas.getByRole("button", { name: "Viewer" })
    ).toHaveAttribute("aria-pressed", "true")

    await userEvent.type(
      canvas.getByLabelText("Email"),
      "friend@example.com"
    )
    await expect(canvas.getByRole("button", { name: "Share" })).toBeEnabled()
  },
}
