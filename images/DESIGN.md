---
name: Theatrical Proscenium
colors:
  surface: '#131317'
  surface-dim: '#131317'
  surface-bright: '#39393d'
  surface-container-lowest: '#0e0e12'
  surface-container-low: '#1b1b1f'
  surface-container: '#1f1f24'
  surface-container-high: '#2a292e'
  surface-container-highest: '#353439'
  on-surface: '#e4e1e7'
  on-surface-variant: '#e4beb9'
  inverse-surface: '#e4e1e7'
  inverse-on-surface: '#303034'
  outline: '#ab8985'
  outline-variant: '#5b403d'
  surface-tint: '#ffb4ac'
  primary: '#ffb4ac'
  on-primary: '#690006'
  primary-container: '#ff544c'
  on-primary-container: '#5c0005'
  inverse-primary: '#bb171c'
  secondary: '#ffb3b3'
  on-secondary: '#680015'
  secondary-container: '#960423'
  on-secondary-container: '#ff9e9f'
  tertiary: '#e9c349'
  on-tertiary: '#3c2f00'
  tertiary-container: '#cca830'
  on-tertiary-container: '#4f3e00'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdad6'
  primary-fixed-dim: '#ffb4ac'
  on-primary-fixed: '#410002'
  on-primary-fixed-variant: '#93000d'
  secondary-fixed: '#ffdad9'
  secondary-fixed-dim: '#ffb3b3'
  on-secondary-fixed: '#400009'
  on-secondary-fixed-variant: '#920021'
  tertiary-fixed: '#ffe088'
  tertiary-fixed-dim: '#e9c349'
  on-tertiary-fixed: '#241a00'
  on-tertiary-fixed-variant: '#574500'
  background: '#131317'
  on-background: '#e4e1e7'
  surface-variant: '#353439'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 42px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 30px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 21px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  stage-direction:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-character:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.08em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system channels the atmospheric intensity of an auditorium before curtain call: velvet drapery in deep crimson, charcoal backstage shadows, and sharp stage lights slicing through the dark. It is built specifically for stage actors, voice performers, and directors engaged in memorization, line runs, and rehearsal pacing.

The visual direction blends sleek iOS dark utility with dramatic theatrical cues. Interface elements feel like precision instruments calibrated for high-stress memorization:
- **Atmospheric Depth:** The background is grounded in deep backstage black (`#0A0A0E`) and tiered charcoal layers, preventing eye strain during intense, low-light backstage or late-night study sessions.
- **Auditory & Dramatic Focus:** Ruby and vivid crimson highlights (`#E53935`, `#B82638`) serve as spotlight signals that demarcate the user's upcoming lines, active playback scrubbers, and microphone readiness.
- **Theatrical Dignity:** Clean, modern sans-serif typography ensures instant legibility at arm's length or from a music stand, while stage cues and character notations invoke classic proscenium scripts.

## Colors

The palette reproduces the contrast between a dark auditorium and the illuminated stage:

- **Primary (`#E53935`)**: The active rehearsal beacon. Used for the user's active dialogue borders, live audio scrubbers, record status, and critical primary action buttons (such as "Your line — speak now").
- **Secondary (`#B82638`)**: Rich theater velvet red. Employed for active dialogue bubble backgrounds, selected state surfaces, and secondary badges.
- **Tertiary (`#D4AF37`)**: Antique proscenium gold. Reserved exclusively for stage directions, editorial script metadata, parentheticals, and timing cues.
- **Neutral (`#0A0A0E`)**: Stage pit black. Forms the foundational canvas, layered upwards with `#141418` (surface elevated), `#1E1E24` (card containers), and `#2B2B36` (borders and dividing lines).

Text levels prioritize contrast:
- **On-Dark Primary**: `#F8F8FA` (dialogue lines and scene titles)
- **On-Dark Secondary**: `#9E9EA8` (supporting character names, timestamps, and durations)
- **Opposing Character Bubbles**: Finished in `#1A1A22` with a 1px border of `#2C2C38`, keeping focus firmly separated from the user’s vibrant ruby cues.

## Typography

Typography balances dramatic performance presence with high-speed scan efficiency during fast-paced line deliveries:

- **Headlines & Titles (`Plus Jakarta Sans`)**: Clean, authoritative, and contemporary. Display weights set production and script names with cinematic confidence. Character badges utilize uppercase styling with generous tracking (`0.08em`) to ensure instant character identification before speaking.
- **Dialogue & UI Copy (`Inter`)**: Chosen for its open apertures and tall x-height, guaranteeing zero ambiguity between words during rehearsal runs.
- **Stage Directions (`Inter Italic`)**: Set in muted amber (`#D4AF37`) or dim rose (`#A38287`), italicized and bracketed, allowing actors to clearly distinguish physical actions and emotional inflections from spoken text.

## Layout & Spacing

The layout model is tailored for continuous conversational flow, reminiscent of standard script cue sheets and iOS native messaging feeds.

- **Conversation Stream**: Dialogue cards stack vertically with `space-md` (12px) separation between consecutive lines of the same character and `space-lg` (16px) separation between speaker changes.
- **Horizontal Indentation**: Opposing speakers align left with right margin gutters of `3rem` to avoid visual clutter. The user's own lines align right with left margin gutters of `2.5rem`, providing instant visual orientation.
- **Safe Viewports & Controls**: The rehearsal screen reserves a persistent bottom padding zone (`4.5rem`) to accommodate audio scrubbers, floating pill prompts ("Your line — speak now"), and transport bars without obscuring current text.
- **Responsive Adaptations**:
  - *Mobile (<640px)*: Single-column vertical script flow with sticky top navigation header containing script title and character switchers.
  - *Tablet & Desktop (≥768px)*: Dual-pane layout where the left rail holds the scene index, character notes, and audio scrubber, while the right pane presents the active line run.

## Elevation & Depth

Visual hierarchy uses rich dark layering combined with soft ruby backlights:

- **Canvas (Level 0)**: Solid `#0A0A0E`.
- **Card Containers (Level 1)**: Elevated dark charcoal (`#141418` to `#1E1E24`) bordered with subtle 1px low-contrast strokes (`rgba(255, 255, 255, 0.08)`).
- **Active User Dialogue (Level 2 - Stage Highlight)**: Tinted deep ruby translucent container (`rgba(139, 30, 43, 0.35)`) framed by a crisp border (`#B82638`) and a 12px ambient ruby shadow (`rgba(229, 57, 53, 0.2)`).
- **Floating Controls & Glass Badges (Level 3)**: Frosted glass layers utilizing `backdrop-filter: blur(16px)`, `background: rgba(26, 26, 34, 0.75)`, and delicate specular borders (`rgba(255, 255, 255, 0.15)`).
- **Active Floating Action Bar (Level 4)**: The prompt banner ("Your line — speak now") glows with a saturated crimson drop shadow (`0 8px 24px rgba(229, 57, 53, 0.45)`), lifting it above all rehearsal text.

## Shapes

The system relies on roundedness level `2` (8px standard corner radius), blending modern iOS ergonomics with the structured architecture of theatrical cue cards:

- **Dialogue Bubbles**: Formed with 12px corners (`rounded-md` / `rounded-lg` blend) for an approachable, readable block shape that accommodates multi-line monologues without pinching text.
- **Chips & Character Tags**: Pill-rounded (`9999px`) to create clear differentiation against rectilinear dialogue cards.
- **Action Buttons & Scrubbers**: Pill-rounded floating action buttons for comfortable thumb targets during active recitations.
- **Script Thumbnails**: Rounded 8px corners matching production playbill aspect ratios.

## Components

### Dialogue Cards
- **Opposing Character Card**: Surface `#1A1A22`, 1px border `#2A2A36`. Character name sits above dialogue in uppercase `label-character` using a muted coral or grey tone (`#D47A7A` or `#8E8E9A`). Internal stage directions appear below or inline in `stage-direction` style.
- **Active User Card**: Surface `rgba(139, 30, 43, 0.25)` with a solid `#B82638` or `#E53935` border outline. Character name displayed in saturated ruby tint.

### Audio Scrubbers & Progress Tracks
- **Track**: 2px or 3px height in `#2B2B36`.
- **Elapsed Fill**: Vivid `#E53935` progress indicator accompanied by a subtle 4px glow.
- **Timestamps**: Positioned at track terminus in `label-sm` (`#7A7A88`).

### Theatrical Prompt Button (Floating Rehearsal CTA)
- Elevated pill pinned to bottom-center screen.
- Surface: `#E53935` gradient to `#B82638`.
- Content: Audio waveform or microphone glyph paired with white bold text: "Your line — speak now".
- Visual treatment: Pulsing ambient crimson aura signaling voice input readiness.

### Script List & Rehearsal Index
- Rows bounded by subtle bottom borders (`#1E1E26`).
- Left playbill poster thumb (48x48px) with script cover title.
- Title in `headline-sm`, subtitle with line completion counter and rehearsal duration in `body-sm`.
- Embedded progress track along card base indicating completion rate.

### Glass Badges & Header Selectors
- Frosted glass pills housing comedy/tragedy mask icons paired with the active character persona (e.g., `[🎭 LOMOV]`).
- Backing: `rgba(30, 30, 40, 0.65)` with `1px solid rgba(255, 255, 255, 0.12)`.