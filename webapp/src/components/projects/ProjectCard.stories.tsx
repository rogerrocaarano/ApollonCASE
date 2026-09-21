import type { Meta, StoryObj } from "@storybook/react-vite"
import { fn } from "storybook/test"
import type { Project } from "@/types"
import { ProjectCard } from "./ProjectCard"

const SAMPLE_PROJECT: Project = {
  id: "p1",
  name: "Architecture diagrams",
  description: "C4 model of the extension-backend gateway.",
  myPermission: "OWNER",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-05T00:00:00.000Z",
}

/**
 * A single project tile: name, description, last-modified date, rename and
 * delete actions. Deliberately lighter than `DiagramCard` — no thumbnail,
 * favorite, or local/shared distinction, since none of those apply to a
 * project.
 */
const meta = {
  title: "Webapp/Projects/ProjectCard",
  component: ProjectCard,
  tags: ["autodocs"],
  decorators: [
    (Story) => (
      // `role="listitem"`; wrap in the `role="list"` container the production
      // gallery provides (axe: aria-required-parent).
      <div role="list" style={{ width: 300 }}>
        <Story />
      </div>
    ),
  ],
  parameters: { layout: "centered" },
  args: {
    project: SAMPLE_PROJECT,
    onRename: fn(),
    onDelete: fn(),
  },
} satisfies Meta<typeof ProjectCard>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}

export const NoDescription: Story = {
  args: {
    project: { ...SAMPLE_PROJECT, description: "" },
  },
}

export const Untitled: Story = {
  args: {
    project: { ...SAMPLE_PROJECT, name: "", description: "" },
  },
}

/** Without a handler, its action (Rename/Delete) is omitted from the menu. */
export const WithoutRenameAction: Story = {
  args: {
    onRename: undefined,
  },
}

/** Without either handler, the actions menu is not rendered at all. */
export const WithoutActions: Story = {
  args: {
    onRename: undefined,
    onDelete: undefined,
  },
}
