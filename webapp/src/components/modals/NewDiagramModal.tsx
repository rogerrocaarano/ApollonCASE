import { useState } from "react"
import { toast } from "react-toastify"
import { useModalContext } from "@/contexts/ModalContext"
import { UMLDiagramType, type UMLModel } from "@tumaet/apollon"
import { useNavigate } from "@tanstack/react-router"
import { usePersistenceModelStore } from "@/stores/usePersistenceModelStore"
import { getDiagramTypeIcon } from "@/components/home/diagramTypeMeta"
import { TemplateThumbnail } from "./TemplateThumbnail"
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@tumaet/ui/components/tabs"
import { log } from "@/logger"
import { prepareTemplateModel } from "@/utils/templateModels"
import { ProjectsApiClient } from "@/services/ExtensionApiClient"
import {
  HomeDialogActions,
  HomeDialogContent,
  HomeDialogField,
  HomeDialogNotice,
  type HomeDialogOption,
  HomeDialogOptionGroup,
  HomeDialogTextInput,
} from "./HomeDialog"

const diagramTypes = {
  structural: [
    UMLDiagramType.ClassDiagram,
    UMLDiagramType.ObjectDiagram,
    UMLDiagramType.ComponentDiagram,
    UMLDiagramType.DeploymentDiagram,
    UMLDiagramType.Flowchart,
    UMLDiagramType.SyntaxTree,
    UMLDiagramType.Sfc,
  ],
  behavioral: [
    UMLDiagramType.ActivityDiagram,
    UMLDiagramType.UseCaseDiagram,
    UMLDiagramType.CommunicationDiagram,
    UMLDiagramType.PetriNet,
    UMLDiagramType.ReachabilityGraph,
    UMLDiagramType.BPMN,
  ],
}

const diagramTypeToTitle: Record<UMLDiagramType, string> = {
  ClassDiagram: "Class Diagram",
  ObjectDiagram: "Object Diagram",
  ActivityDiagram: "Activity Diagram",
  UseCaseDiagram: "Use Case Diagram",
  CommunicationDiagram: "Communication Diagram",
  ComponentDiagram: "Component Diagram",
  DeploymentDiagram: "Deployment Diagram",
  PetriNet: "Petri Net",
  ReachabilityGraph: "Reachability Graph",
  SyntaxTree: "Syntax Tree",
  Flowchart: "Flowchart",
  BPMN: "BPMN Diagram",
  Sfc: "Sequential Function Chart Diagram",
}

const toDiagramOption = (
  type: UMLDiagramType
): HomeDialogOption<UMLDiagramType> => ({
  value: type,
  label: diagramTypeToTitle[type],
  icon: getDiagramTypeIcon(type, "h-7 w-7"),
})

const structuralDiagramOptions: HomeDialogOption<UMLDiagramType>[] =
  diagramTypes.structural.map(toDiagramOption)

const behavioralDiagramOptions: HomeDialogOption<UMLDiagramType>[] =
  diagramTypes.behavioral.map(toDiagramOption)

enum TemplateType {
  Adapter = "Adapter",
  Bridge = "Bridge",
  Command = "Command",
  Observer = "Observer",
  Factory = "Factory",
}

// Built once at module scope (icon elements have stable identity) so typing in
// the name field doesn't rebuild the option arrays and re-render the previews.
const toTemplateOption = (
  template: TemplateType
): HomeDialogOption<TemplateType> => ({
  value: template,
  label: template,
  icon: <TemplateThumbnail name={template} />,
})

const structuralTemplates: HomeDialogOption<TemplateType>[] = [
  TemplateType.Adapter,
  TemplateType.Bridge,
].map(toTemplateOption)

const behavioralTemplates: HomeDialogOption<TemplateType>[] = [
  TemplateType.Command,
  TemplateType.Observer,
].map(toTemplateOption)

const creationalTemplates: HomeDialogOption<TemplateType>[] = [
  TemplateType.Factory,
].map(toTemplateOption)

export interface NewDiagramModalProps {
  /**
   * When set, the modal was opened from a project: creation goes through
   * `diagrams-backend` + `extension-backend`'s link endpoint and lands in the
   * collaborative editor, instead of the local (IndexedDB-only) store. See
   * design.md's "New diagram creation flow" decision in the `add-projects`
   * change.
   */
  projectId?: string
}

export const NewDiagramModal = ({ projectId }: NewDiagramModalProps) => {
  const { closeModal } = useModalContext()
  const [activeTab, setActiveTab] = useState<"scratch" | "template">("scratch")
  const [selectedDiagramType, setSelectedDiagramType] =
    useState<UMLDiagramType>(UMLDiagramType.ClassDiagram)
  const [selectedTemplate, setSelectedTemplate] = useState<TemplateType>(
    TemplateType.Adapter
  )
  // Whether the name is still the auto-default (vs. user-typed). A blank/scratch
  // diagram defaults to NO name (created untitled — home/editor show a muted
  // placeholder); a template keeps its own name as the sensible default.
  const [isDiagramNameDefault, setIsDiagramNameDefault] =
    useState<boolean>(true)
  const [newDiagramTitle, setNewDiagramTitle] = useState<string>("")
  const [error, setError] = useState<string | null>(null)
  const [isCreatingInProject, setIsCreatingInProject] = useState(false)
  const navigate = useNavigate()
  const createModelByTitleAndType = usePersistenceModelStore(
    (state) => state.createModelByTitleAndType
  )
  const createModel = usePersistenceModelStore((state) => state.createModel)

  // extension-backend creates the diagram body in diagrams-backend
  // server-side and links it to the project in one call — the browser never
  // sees the diagrams-backend id (see gate-project-diagrams). Then opens it
  // in the project-diagram collaborative editor.
  const createDiagramInProject = async (model: UMLModel) => {
    if (!projectId) return
    setIsCreatingInProject(true)
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
      setIsCreatingInProject(false)
    }
  }

  const handleCreateDiagram = () => {
    if (projectId) {
      void createDiagramInProject({
        id: crypto.randomUUID(),
        type: selectedDiagramType,
        assessments: {},
        edges: [],
        nodes: [],
        title: newDiagramTitle,
        version: "4.0.0",
      })
      return
    }

    const newId = createModelByTitleAndType(
      newDiagramTitle,
      selectedDiagramType
    )
    closeModal()
    navigate({ to: "/local/$id", params: { id: newId } })
  }

  const handleDiagramNameChange = (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    setNewDiagramTitle(event.target.value)
    setIsDiagramNameDefault(false)
  }

  // Auto-default only: scratch → empty (untitled), template → the template name.
  // A type change never names a scratch diagram (it stays untitled).
  const handleTabChange = (tab: "scratch" | "template") => {
    setActiveTab(tab)
    if (isDiagramNameDefault) {
      setNewDiagramTitle(tab === "template" ? selectedTemplate : "")
    }
  }

  const handleDiagramTypeChange = (type: UMLDiagramType) => {
    setSelectedDiagramType(type)
  }

  const handleTemplateChange = (template: TemplateType) => {
    setSelectedTemplate(template)
    if (isDiagramNameDefault) {
      setNewDiagramTitle(template)
    }
  }

  const handleCreateFromTemplate = async () => {
    setError(null)

    try {
      const jsonModule = await import(
        `assets/diagramTemplates/${selectedTemplate}.json`
      )
      const jsonData = jsonModule.default

      if (!jsonData) {
        throw new Error("Selected template data not found")
      }

      // Templates ship with fixed ids (several even share one), so give each
      // created diagram a fresh id — otherwise creating two collides on the
      // same store key and one silently overwrites the other. Preparing also
      // removes stale React Flow interaction flags captured in the JSON asset
      // without discarding deliberate pinned endpoints or authored bends.
      const templateModel = prepareTemplateModel(jsonData, {
        id: crypto.randomUUID(),
        title: newDiagramTitle,
      })

      if (projectId) {
        await createDiagramInProject(templateModel)
        return
      }

      createModel(templateModel)
      closeModal()
      navigate({ to: "/local/$id", params: { id: templateModel.id } })
    } catch (err: unknown) {
      log.error("Error creating diagram from template:", err as Error)

      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError("An unexpected error occurred")
      }
    }
  }

  return (
    <HomeDialogContent>
      <Tabs
        value={activeTab}
        onValueChange={(value) =>
          handleTabChange(value as "scratch" | "template")
        }
      >
        <TabsList>
          <TabsTrigger value="scratch">Blank diagram</TabsTrigger>
          <TabsTrigger value="template">Use template</TabsTrigger>
        </TabsList>

        {error && <HomeDialogNotice>{error}</HomeDialogNotice>}

        <HomeDialogField label="Name" htmlFor="diagram-title">
          <HomeDialogTextInput
            id="diagram-title"
            value={newDiagramTitle}
            onChange={handleDiagramNameChange}
            placeholder="Enter diagram name"
          />
        </HomeDialogField>

        <TabsContent value="scratch" className="flex flex-col gap-5">
          <div className="flex flex-col gap-4">
            <section className="flex flex-col gap-2">
              <h3 className="text-xs font-semibold text-foreground">
                Structural Diagrams
              </h3>
              <HomeDialogOptionGroup
                label="Structural Diagrams"
                options={structuralDiagramOptions}
                value={selectedDiagramType}
                onChange={handleDiagramTypeChange}
                onConfirm={handleCreateDiagram}
                hideLabel
              />
            </section>

            {diagramTypes.behavioral.length > 0 && (
              <section className="flex flex-col gap-2">
                <h3 className="text-xs font-semibold text-foreground">
                  Behavioral Diagrams
                </h3>
                <HomeDialogOptionGroup
                  label="Behavioral Diagrams"
                  options={behavioralDiagramOptions}
                  value={selectedDiagramType}
                  onChange={handleDiagramTypeChange}
                  onConfirm={handleCreateDiagram}
                  hideLabel
                />
              </section>
            )}
          </div>
        </TabsContent>

        <TabsContent value="template" className="flex flex-col gap-5">
          <section className="flex flex-col gap-2">
            <h3 className="text-xs font-semibold text-foreground">
              Structural
            </h3>
            <HomeDialogOptionGroup
              label="Structural"
              options={structuralTemplates}
              value={selectedTemplate}
              onChange={handleTemplateChange}
              onConfirm={() => void handleCreateFromTemplate()}
              columns={2}
              hideLabel
            />
          </section>

          <section className="flex flex-col gap-2">
            <h3 className="text-xs font-semibold text-foreground">
              Behavioral
            </h3>
            <HomeDialogOptionGroup
              label="Behavioral"
              options={behavioralTemplates}
              value={selectedTemplate}
              onChange={handleTemplateChange}
              onConfirm={() => void handleCreateFromTemplate()}
              columns={2}
              hideLabel
            />
          </section>

          <section className="flex flex-col gap-2">
            <h3 className="text-xs font-semibold text-foreground">
              Creational
            </h3>
            <HomeDialogOptionGroup
              label="Creational"
              options={creationalTemplates}
              value={selectedTemplate}
              onChange={handleTemplateChange}
              onConfirm={() => void handleCreateFromTemplate()}
              columns={2}
              hideLabel
            />
          </section>
        </TabsContent>
      </Tabs>

      <HomeDialogActions
        cancelLabel="Cancel"
        confirmLabel="Create Diagram"
        loadingLabel="Creating…"
        loading={isCreatingInProject}
        onCancel={closeModal}
        onConfirm={() => {
          if (activeTab === "scratch") {
            handleCreateDiagram()
            return
          }

          void handleCreateFromTemplate()
        }}
      />
    </HomeDialogContent>
  )
}
