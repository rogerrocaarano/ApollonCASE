import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import {
  ChevronDownIcon,
  CircleHelpIcon,
  LogOutIcon,
  UserPenIcon,
} from "lucide-react"
import { Link } from "@tanstack/react-router"
import { useAuth } from "react-oidc-context"
import { Button } from "@tumaet/ui/components/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@tumaet/ui/components/dropdown-menu"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@tumaet/ui/components/tooltip"
import { useMediaQuery } from "@/hooks"
import { bugReportURL } from "@/constants/urls"
import { releasesLink, repositoryLink } from "@/constants/version"
import {
  navbarButtonStyle,
  CHROME_REVEAL,
  type ChromeReveal,
} from "@/components/navbar/styleConstants"
import { MOBILE_MENU_CONTENT_CLASS } from "@/components/navbar/islandPrimitives"
import type { CurrentUser } from "@/services/ExtensionApiClient"
import { useModalContext } from "@/contexts"
import { CURRENT_USER_QUERY_KEY, useCurrentUser } from "@/hooks/useCurrentUser"
import { useHelpMenu } from "./useHelpMenu"

/**
 * The SINGLE Help/legal menu source for the WHOLE app — home band, home mobile
 * overflow, the chrome sub-route header, AND the editor (desktop + mobile
 * overflow). Both the home and the editor Help controls render the SAME
 * {@link HelpMenuItems} body, so the item SET, ORDER, labels, surface, and a11y
 * are identical everywhere and can never drift.
 *
 * Item order (shared tail): About → Releases → GitHub → Report a problem,
 * separator, then the legal links Imprint → Privacy. The `editor` variant adds
 * the editor-only entries around that tail — "How does this Editor Work?" leads,
 * and (in DEV builds only) "Open Playground" sits just before the legal
 * separator — without forking the surface or the shared items.
 *
 * The legal `<Link>`s forward the INHERITED origin via `state={{ from }}` (the
 * `readNavFrom` provenance idiom) so an editor → legal hop still offers a real
 * "back to diagram".
 */

export type HelpMenuVariant = "home" | "editor"

/**
 * The shared Help/legal item body. Rendered directly inside any
 * `DropdownMenuContent` (the home/editor Help menus) OR inlined into a larger
 * overflow menu (the home mobile "…" pill), so the same items appear with the
 * same labels and order in every surface.
 */
export function HelpMenuItems({
  variant = "home",
  onSelect,
}: {
  variant?: HelpMenuVariant
  /** Closes the surrounding menu after an item is chosen. */
  onSelect: () => void
}) {
  // Impure wiring (modal opening + router-derived legal provenance) lives in the
  // hook; the item rendering below stays pure relative to its outputs.
  const { legalLinkState, openHowToUse, openAbout } = useHelpMenu(variant)
  const auth = useAuth()
  const queryClient = useQueryClient()
  const { openModal } = useModalContext()
  // Exercises the Authorization header end-to-end against extension-backend
  // (see openspec/changes/add-keycloak-auth) - not just a login formality.
  const { data: currentUser } = useCurrentUser()

  const openEditProfile = () => {
    if (!currentUser) return
    openModal("EDIT_PROFILE", {
      dialogVariant: "home",
      user: currentUser,
      onUpdated: (updated: CurrentUser) => {
        queryClient.setQueryData(CURRENT_USER_QUERY_KEY, updated)
      },
    })
  }

  return (
    <>
      {variant === "editor" && (
        <DropdownMenuItem
          onClick={() => {
            openHowToUse()
            onSelect()
          }}
        >
          How does this Editor Work?
        </DropdownMenuItem>
      )}
      <DropdownMenuItem
        onClick={() => {
          openAbout()
          onSelect()
        }}
      >
        About
      </DropdownMenuItem>
      <DropdownMenuItem
        render={
          <a
            href={releasesLink}
            target="_blank"
            rel="noreferrer"
            onClick={onSelect}
          >
            Releases
          </a>
        }
      />
      <DropdownMenuItem
        render={
          <a
            href={repositoryLink}
            target="_blank"
            rel="noreferrer"
            onClick={onSelect}
          >
            GitHub
          </a>
        }
      />
      <DropdownMenuItem
        render={
          <a
            href={bugReportURL}
            target="_blank"
            rel="noreferrer"
            onClick={onSelect}
          >
            Report a problem
          </a>
        }
      />
      {variant === "editor" && import.meta.env.DEV && (
        <DropdownMenuItem
          render={
            <Link to="/playground" onClick={onSelect}>
              Open Playground
            </Link>
          }
        />
      )}
      <DropdownMenuSeparator />
      <DropdownMenuItem
        render={
          <Link to="/imprint" state={legalLinkState} onClick={onSelect}>
            Imprint
          </Link>
        }
      />
      <DropdownMenuItem
        render={
          <Link to="/privacy" state={legalLinkState} onClick={onSelect}>
            Privacy
          </Link>
        }
      />
      {auth.isAuthenticated && (
        <>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            {currentUser && (
              <DropdownMenuLabel className="text-muted-foreground text-xs font-normal">
                Signed in as{" "}
                {currentUser.displayName?.trim() || currentUser.keycloakId}
              </DropdownMenuLabel>
            )}
            <DropdownMenuItem
              onClick={() => {
                onSelect()
                openEditProfile()
              }}
            >
              <UserPenIcon className="size-4" aria-hidden />
              Edit profile
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => {
                onSelect()
                void auth.signoutRedirect()
              }}
            >
              <LogOutIcon className="size-4" aria-hidden />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </>
      )}
    </>
  )
}

/**
 * The Help dropdown: the standardized trigger shared with the editor's File menu
 * — a shadcn `Button variant="ghost" size="sm"` wearing `navbarButtonStyle()`
 * (via `render=`) with a leading question-mark glyph, a label that reveals at the
 * surface's breakpoint, and a trailing `ChevronDownIcon` — so every chrome menu
 * trigger reads as one control. When the label is hidden the shared `Tooltip`
 * names it (disabled once the label shows). `reveal` selects the per-surface
 * label breakpoint (editor band `lg`, home band `wide`, sub-route `always`). The
 * body is the shared {@link HelpMenuItems}.
 */
export function HomeHelpMenu({
  variant = "home",
  className,
  color,
  reveal = "lg",
}: {
  variant?: HelpMenuVariant
  className?: string
  /** Pins an explicit foreground (the themed mobile overflow menu). */
  color?: string
  reveal?: ChromeReveal
}) {
  const [open, setOpen] = useState(false)
  const close = () => setOpen(false)
  const { labelClass, mq } = CHROME_REVEAL[reveal]
  const labelled = useMediaQuery(mq)

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <Tooltip disabled={labelled}>
        <TooltipTrigger
          render={
            <DropdownMenuTrigger
              id="help-menu-button"
              render={
                <Button
                  variant="ghost"
                  size="sm"
                  className={navbarButtonStyle(className)}
                  style={color ? { color } : undefined}
                  aria-label="Help"
                />
              }
            >
              <CircleHelpIcon className="size-4" aria-hidden />
              <span className={labelClass}>Help</span>
              <ChevronDownIcon className="size-4" aria-hidden />
            </DropdownMenuTrigger>
          }
        />
        <TooltipContent>Help</TooltipContent>
      </Tooltip>
      <DropdownMenuContent
        aria-labelledby="help-menu-button"
        className={MOBILE_MENU_CONTENT_CLASS}
      >
        <HelpMenuItems variant={variant} onSelect={close} />
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
