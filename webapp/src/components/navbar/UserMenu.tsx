import { useState } from "react"
import { ChevronDownIcon, LogOutIcon, UserIcon } from "lucide-react"
import { useAuth } from "react-oidc-context"
import { Button } from "@tumaet/ui/components/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@tumaet/ui/components/dropdown-menu"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@tumaet/ui/components/tooltip"
import { useMediaQuery } from "@/hooks"
import { useTokenIdentity } from "@/hooks/useTokenIdentity"
import {
  navbarButtonStyle,
  CHROME_REVEAL,
  type ChromeReveal,
} from "./styleConstants"
import { MOBILE_MENU_CONTENT_CLASS } from "./islandPrimitives"

/**
 * The account control: who's signed in, and a way to sign out. Extracted from
 * `HomeHelpMenu`'s former "Signed in as / Edit profile / Sign out" tail —
 * there's nothing left to edit (email and display name both come from the
 * Keycloak token, see `fix-identity-and-nav-gaps`), so this is just identity +
 * sign-out, as its own control rather than buried inside Help. Renders
 * nothing when unauthenticated.
 */
export function UserMenu({
  className,
  color,
  reveal = "lg",
}: {
  className?: string
  /** Pins an explicit foreground (the themed mobile overflow menu). */
  color?: string
  reveal?: ChromeReveal
}) {
  const [open, setOpen] = useState(false)
  const auth = useAuth()
  const { displayName, email } = useTokenIdentity()
  const { labelClass, mq } = CHROME_REVEAL[reveal]
  const labelled = useMediaQuery(mq)

  if (!auth?.isAuthenticated) return null

  const signedInAs = [displayName, email].filter(Boolean).join(" · ")

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <Tooltip disabled={labelled}>
        <TooltipTrigger
          render={
            <DropdownMenuTrigger
              id="user-menu-button"
              render={
                <Button
                  variant="ghost"
                  size="sm"
                  className={navbarButtonStyle(className)}
                  style={color ? { color } : undefined}
                  aria-label="Account"
                />
              }
            >
              <UserIcon className="size-4" aria-hidden />
              <span className={labelClass}>Account</span>
              <ChevronDownIcon className="size-4" aria-hidden />
            </DropdownMenuTrigger>
          }
        />
        <TooltipContent>Account</TooltipContent>
      </Tooltip>
      <DropdownMenuContent
        aria-labelledby="user-menu-button"
        className={MOBILE_MENU_CONTENT_CLASS}
      >
        <DropdownMenuGroup>
          {signedInAs && (
            <DropdownMenuLabel className="text-muted-foreground text-xs font-normal">
              Signed in as {signedInAs}
            </DropdownMenuLabel>
          )}
          <DropdownMenuItem
            onClick={() => {
              setOpen(false)
              void auth.signoutRedirect()
            }}
          >
            <LogOutIcon className="size-4" aria-hidden />
            Sign out
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
