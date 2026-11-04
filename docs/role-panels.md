# Role panels and medical references

Role panels are optional local views of the active incident. Open them from
**View > Role Panels**: Incident Command, Safety Officer, Medical Unit Leader,
Operations, or Communications Unit. They are workflow conveniences, not
permissions or separate incident records. The full editors remain available
through the main workspace and are reached using stable workspace identifiers.

The Incident Command panel supports adding and editing objective wording,
assessment, and assessment notes while preserving objective identity and
assignment links. Its objective context can link existing assignments or create
a normal ICS 204 assignment prelinked to the objective. The linked-assignment
window retains the existing ICS 204, SAR task, logging, status, and debrief
actions, and has a separate reported-results view with operator-stated sources.
Command emphasis and general situational awareness from ICS 202 are editable in
the panel. Objectives without linked assignments are highlighted in the table;
the Objectives badge also reports objectives without linked work and assignments
without a current objective link. There is no separate attention panel. The panel
does not calculate objective achievement, resource readiness, or duplicate
resource totals. The assessment column is hidden by default and can be toggled
from **Configuration > Show objective assessments**; the objective editor omits
assessment fields when the column is hidden. The recorded resource overview
initially groups counts by resource type.

The Operations task board offers the same assignment, status, logging, clue, and
debrief actions in its task context menu. Debrief is enabled only for returned
tasks. Its available-resource list reflects T-cards recorded as available.
SAR-task quick-log entries open the shared activity editor with the event type
and task narrative prefilled. The application-wide event-type manager controls
which built-in or custom event types appear there; custom types remain local
application preferences.

Communications includes an explicit action to add an ICP ICS 214 log entry. The
ICP Activity Timeline is a separate modeless window with normal maximize and
minimize controls.

The plan-review action records an explicit review assertion about current
content. It is not objective approval, authentication, or an electronic
signature. Changing a legacy plan status field does not record that assertion;
use the explicit review action when a review is being recorded.

Human Medical Reference — ICS 206 and Canine Medical Reference — adapted ICS
206CANINE are separate, nonmodal windows. They show the corresponding plan and
provide navigation to its full editor. Opening a reference does not create an
activity-log entry or automatically change incident state; use its explicit
logging action when an event should be recorded. The references do not combine
human and canine plan content.

All views use the same local incident model. Role selection does not enforce
authorization, and this UI adds no remote persistence or automatic incident
events. Canine workspace and reference instances share `CanineMedicalFacilityTools`
for directory lookup, stable-ID facility selection and details, and route
presentation. The tool also delegates the workspace's explicit shortlist and
route-calculation actions to existing medical services. Each surface owns its own
Swing components; the reference uses only read-only operations and never invokes
a geocoder or routing provider. Explicit editing, geocoding, route calculation,
and plan selection remain in the canine workspace.

The former **View > Dashboard** role-based tab ordering has been retired.
Workspaces now use one stable order; use **View > Role Panels** for focused role
views. A saved legacy dashboard preference is ignored.

The Incident Command panel provides focused objective editing, assessment,
assignment linking/creation, and reported results with stated sources. Automated
coverage and the remaining optional accessibility follow-up are recorded in
[`DraftWorkPlan.md`](../DraftWorkPlan.md); resizing and second-screen use are
user-confirmed. Keyboard-only navigation is not an acceptance gate.
