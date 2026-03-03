CARDEA DESIGN SPECIFICATION



Version: 1.1

Scope: Full visual redesign (UI/UX only)

Content and logic locked



0\. PRIMARY RULES (CRITICAL)



The AI agent MUST follow these constraints:



Allowed



The agent MAY change:



colors



materials



spacing



typography



icon style



card layout



grouping



padding



visual hierarchy



graph styling



glass effects



gradients



glow effects



navigation bar appearance



Across ALL tabs:



Home

Workout

History

Progress

Account



NOT Allowed



The agent MUST NOT change:



tab names



tab count



navigation order



data shown



metrics shown



calculations



formulas



business logic



database schema



graph data



labels of metrics



Example:



Allowed:



Change graph color.



Not allowed:



Change "Resting Heart Rate" to "Heart Health Score"



1\. GLOBAL DESIGN TOKENS



These MUST be implemented and used everywhere.



1.1 Base Background



Primary app background:



--cardea-bg-primary: #0B0F17;



Secondary gradient layer:



--cardea-bg-secondary: #0F1623;



Final background rendering:



background:

radial-gradient(

circle at top left,

\#0F1623 0%,

\#0B0F17 60%

);

1.2 Glass Surface System



Base glass material:



--cardea-glass-fill:

linear-gradient(

180deg,

rgba(255,255,255,0.06),

rgba(255,255,255,0.02)

);



Glass border:



--cardea-glass-border:

rgba(255,255,255,0.06);



Glass highlight edge:



--cardea-glass-highlight:

rgba(255,255,255,0.08);



Backdrop blur:



backdrop-filter: blur(12px);



Corner radius:



border-radius: 18px;

1.3 Cardea Core Gradient



This gradient defines the brand identity.



Use EXACT values.



--cardea-gradient-primary:

linear-gradient(

135deg,

\#FF5A5F 0%,

\#FF2DA6 35%,

\#5B5BFF 65%,

\#00D1FF 100%

);



Do NOT alter stops.



1.4 Text Colors



Primary text:



--cardea-text-primary: #FFFFFF;



Secondary text:



--cardea-text-secondary: #9AA4B2;



Inactive:



--cardea-text-tertiary: #5A6573;

1.5 Graph Rendering System



All graphs MUST use this visual model.



Core line:



stroke-width: 2px;

stroke: var(--cardea-gradient-primary);



Glow layer:



Duplicate line behind:



filter: blur(6px);

opacity: 0.15;



Cap style:



stroke-linecap: round;



This rule applies to ALL graphs across ALL tabs.



2\. GLOBAL CARD SYSTEM



All metrics MUST be contained inside glass cards.



Card padding:



16px



Card spacing:



16px vertical spacing



Do NOT place metrics directly on background.



3\. NAVIGATION BAR VISUAL SPEC



Applies to ALL tabs.



Height:



72px



Material:



Glass.



Background:



linear-gradient(

180deg,

rgba(255,255,255,0.08),

rgba(255,255,255,0.03)

);



Blur:



blur(16px)



Active icon:



Use gradient fill.



Inactive icon:



var(--cardea-text-tertiary)



Icon size:



24px



Label size:



11px

4\. HOME TAB VISUAL SPEC (DETAILED)



The Home tab is the visual anchor of the app.



Purpose:



Present current physiological state and primary action.



Structure:



Vertical scroll.



Horizontal padding:



16px



Spacing between cards:



16px

4.1 Efficiency Ring



Diameter:



90px



Ring thickness:



6px



Background ring:



rgba(255,255,255,0.08)



Foreground:



Use gradient.



Inner number:



32px font

weight 500

4.2 Start Workout Button



Height:



56px



Background:



Gradient.



Radius:



14px



Press state:



filter: brightness(0.92)

4.3 Graph Cards



All graph cards must follow:



Glass container



Line graph using gradient and glow spec.



Grid lines:



rgba(255,255,255,0.04)

5\. WORKOUT TAB VISUAL SPEC (UI ONLY)



Do NOT change content.



Allowed visual changes:



Convert screen into:



Glass layered layout.



Metrics hierarchy:



Primary metric largest



Secondary metrics smaller



Use gradient ONLY for:



Active recording indicators



Do NOT recolor everything.



6\. HISTORY TAB VISUAL SPEC (UI ONLY)



Convert list items into glass cards.



Each workout entry:



Glass card



Map preview:



Dark map



Route line uses gradient and glow.



Spacing:



16px

7\. PROGRESS TAB VISUAL SPEC (UI ONLY)



Keep all existing metrics.



Convert into:



Glass card sections.



All graphs:



Use gradient + glow spec.



Spacing:



16px

8\. ACCOUNT TAB VISUAL SPEC



Glass card sections.



Profile card at top.



Settings list below.



Row height:



52px



Dividers:



rgba(255,255,255,0.06)



Accent color:



Use sparingly.



Only for:



Active toggles.



9\. MOTION SPEC



Animation duration:



250ms



Easing:



ease-out



Allowed animations:



Fade

Position



Do NOT use bounce.



10\. FINAL VISUAL PRINCIPLES



The agent MUST ensure:



Every screen feels:



calm

precise

premium



NOT:



colorful

busy

gamified



Accent color must remain rare and meaningful.

