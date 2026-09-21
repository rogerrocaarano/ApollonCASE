import type { Meta, StoryObj } from "@storybook/react-vite"
import { fn } from "storybook/test"
import type { Project } from "@/types"
import { ProjectGallery } from "./ProjectGallery"

const SAMPLE_PROJECTS: Project[] = [
  {
    id: "p1",
    name: "Architecture diagrams",
    description: "C4 model of the extension-backend gateway.",
    myPermission: "OWNER",
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-05T00:00:00.000Z",
  },
  {
    id: "p2",
    name: "Domain model",
    description: "",
    myPermission: "OWNER",
    createdAt: "2025-12-01T00:00:00.000Z",
    updatedAt: "2025-12-20T00:00:00.000Z",
  },
  {
    id: "p3",
    name: "Onboarding flow",
    description: "Use-case and activity diagrams for the sign-up flow.",
    myPermission: "OWNER",
    createdAt: "2025-11-01T00:00:00.000Z",
    updatedAt: "2025-11-02T00:00:00.000Z",
  },
]

/**
 * The Projects screen's grid: one card per project, plus the empty state
 * shown when the authenticated user has no projects yet.
 */
const meta = {
  title: "Webapp/Projects/ProjectGallery",
  component: ProjectGallery,
  parameters: { layout: "padded" },
  args: {
    projects: SAMPLE_PROJECTS,
    onRename: fn(),
    onDelete: fn(),
  },
} satisfies Meta<typeof ProjectGallery>

export default meta
type Story = StoryObj<typeof meta>

export const Populated: Story = {}

export const Empty: Story = {
  args: {
    projects: [],
  },
}
