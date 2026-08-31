# ResolveIT — Feature Specification

## 1. Project Overview

ResolveIT is an incident management and support platform with exactly two roles:

1. USER
2. SUPPORT

The main purpose of ResolveIT is to allow users to report incidents, automatically assign them to the most suitable support engineer, enable real-time communication between the user and support engineer, assist support engineers using OpsAI, and maintain complete incident history for future analysis.

Each incident is an independent unit of work. Every incident has its own conversation, status history, root cause, resolution, and historical information.

The system focuses on four major areas:

- Incident Management
- Intelligent Automatic Assignment
- Incident-Specific Real-Time Conversation
- AI-Assisted Incident Investigation and Historical Intelligence

---

# 2. Roles

ResolveIT has only two roles.

## USER

The USER is responsible for reporting and tracking incidents.

The USER can:

- Register their own account
- Login
- Logout, ending the session immediately
- View their dashboard
- Report a new incident
- View their own incidents
- View incident details
- Track incident status
- Communicate with the assigned support engineer
- Send messages
- Receive support replies
- View messages in real time
- View message read status
- View complete conversation history
- View root cause after it has been identified
- View resolution details
- View resolved incidents
- View incident history
- Receive real-time updates

The USER cannot:

- Select the support team
- Select the support engineer
- Manually assign an incident
- Change the assigned support engineer
- Confirm the technical root cause
- Resolve an incident
- Access other users' incidents
- Access support-only features
- Choose their own role when registering
- Register as SUPPORT

## SUPPORT

The SUPPORT user is responsible for handling incidents assigned to them.

SUPPORT accounts are created by a SUPER_ADMIN through `POST /api/support-users`,
which assigns the engineer to a team at the same time. There is no SUPPORT
self-registration.

Every SUPPORT account belongs to a team. That team is what makes the engineer
eligible for automatic assignment: an incident is routed to the team owning the
affected service, and only engineers on that team are scored.

The SUPPORT user can:

- Login
- Logout, ending the session immediately
- View the support dashboard
- View assigned incidents
- Open an assigned incident
- View complete incident details
- Communicate with the user
- Send messages
- Receive user messages in real time
- View conversation history
- View message read status
- Update incident status
- Investigate incidents
- Use OpsAI
- Summarize conversations
- Find similar historical incidents
- Analyze incidents
- Get possible root-cause suggestions
- Get resolution recommendations
- Confirm the final root cause
- Add resolution details
- Resolve incidents
- View incident history
- View support analytics

The SUPPORT user cannot:

- Self-register
- Access incidents outside their authorization
- Automatically accept AI recommendations
- Allow AI to make the final root-cause decision
- Resolve an incident without completing the required resolution workflow

---

# 3. USER Dashboard

After login, the USER is taken to the User Dashboard.

The dashboard should display:

- Welcome message
- My Incidents
- Active incidents
- Resolved incidents
- Incident ID
- Incident title
- Current incident status
- Option to report a new incident

Example incident information:

- INC-1024 — Payment Failure — IN PROGRESS
- INC-1018 — Login Issue — RESOLVED

The USER should be able to select an incident and open its complete incident page.

---

# 4. Report New Incident

The USER can report a new incident.

The USER provides:

- Incident title
- Incident description

The description should clearly explain the problem experienced by the user.

Example:

Title:
Payment Failure

Description:
Payment is failing after clicking the Pay button.

The USER should not manually select a support engineer or support team.

The system is responsible for determining where the incident should be handled.

---

# 5. AI-Assisted Incident Classification

After the USER enters the incident title and description, OpsAI analyzes the provided information.

OpsAI suggests:

- Service
- Category
- Severity

For example:

Title:
Payment Failure

Description:
Payment fails after clicking Pay.

OpsAI may suggest:

Service: Payment Service
Category: Payment Failure
Severity: HIGH

The AI classification is only a recommendation.

The system must not blindly accept the AI-generated classification.

---

# 6. Classification Review

Before submitting the incident, the USER can review the AI-generated classification.

The USER can:

- Accept the suggested Service
- Accept the suggested Category
- Accept the suggested Severity
- Modify the Service
- Modify the Category
- Modify the Severity

After reviewing the classification, the USER confirms and submits the incident.

The final values used for the incident are the values confirmed by the USER.

The USER still does not select the support team or support engineer.

---

# 7. Priority

Priority is determined by the system based on incident information and configured business rules.

The USER does not directly choose the final priority.

Priority can consider factors such as:

- Severity
- Service
- Business impact
- Incident characteristics
- Configured priority rules

Example:

Severity: HIGH

Priority: P1

The final priority is associated with the incident and can be viewed by both USER and SUPPORT according to their permissions.

## Current priority mapping

The implemented mapping is severity-driven:

| Severity | Priority |
|----------|----------|
| CRITICAL | P1       |
| HIGH     | P1       |
| MEDIUM   | P3       |
| LOW      | P4       |

The current priority mapping intentionally does not use a separate P2 band.

This follows directly from the worked example above: HIGH must yield P1, which
places CRITICAL and HIGH in the same top band and leaves no severity to occupy
P2. P2 remains a defined priority value so the scale stays complete and so a
future rule that considers service or business impact could use it, but no
incident is assigned P2 under the present severity-only rule.

This mapping is deliberate and must not be widened without also revisiting the
HIGH to P1 example above.

---

# 8. Automatic Assignment

Once an incident is submitted, the system automatically determines the appropriate support team and support engineer.

The USER does not choose:

- Support team
- Support engineer

The system first identifies the service associated with the incident.

The service has an associated owner team.

The system then finds eligible support engineers belonging to the appropriate support team.

The assignment algorithm evaluates eligible engineers and selects the engineer with the highest overall assignment score.

The assigned engineer receives the incident in the SUPPORT dashboard.

---

# 9. Intelligent Assignment Algorithm

The assignment algorithm should not simply assign every incident to the first available engineer.

It should consider multiple factors to find the most suitable engineer while also maintaining workload balance and fairness.

The main factors are:

1. Similar Incident Experience
2. Availability
3. Current Workload
4. Idle Time / Assignment Fairness

Recommended weighting:

- Similar Incident Experience: 40%
- Availability: 25%
- Workload Balance: 20%
- Idle Time / Fairness: 15%

Total: 100%

These weights are used to calculate the final assignment score.

The engineer with the highest final score is selected.

---

# 10. Similar Incident Experience

The system should determine how experienced an engineer is with incidents similar to the new incident.

Similarity can consider:

- Service
- Category
- Incident title
- Incident description
- Severity
- Incident characteristics
- Historical root cause
- Historical resolution

Example:

Arjun has solved 18 similar incidents.

Priya has solved 11 similar incidents.

Rahul has solved 4 similar incidents.

Arjun should receive a higher experience score because he has greater experience with similar incidents.

The experience score should be normalized so that experience does not completely dominate all other assignment factors.

---

# 11. Availability

The assignment system must consider whether an engineer is currently available to handle another incident.

Possible availability states include:

- AVAILABLE
- BUSY
- OFFLINE

OFFLINE engineers should not be considered eligible for assignment.

AVAILABLE engineers should receive a higher availability score.

BUSY engineers can receive a lower score depending on their workload and system rules.

Availability should be evaluated together with workload and experience.

## How availability is determined

Support engineer availability is derived by the application from the existing
assignment/eligibility rules. A separate ONLINE/OFFLINE availability column is
not stored in the database.

The existing seven-table schema is unchanged: `Database_Schema.md` section 31
states that no availability column exists and that the schema must not be
extended to add one. The application therefore derives availability from the
engineer's current active incident load at the moment of assignment:

- below the busy threshold, an engineer is treated as AVAILABLE
- at or above it, the engineer is treated as BUSY and scores lower

Because availability is derived from live workload rather than read from a
stored flag, no engineer is ever derived as OFFLINE, and a team is always able
to accept work. The OFFLINE state remains part of this specification, and the
eligibility rule that excludes OFFLINE engineers remains implemented, so the
behaviour is already correct should an availability source ever be introduced.

---

# 12. Workload Balance

The assignment system should consider the current workload of each eligible engineer.

Workload should not be calculated only by counting incidents.

Workload can consider:

- Number of active incidents
- Severity of active incidents
- Priority of active incidents
- Age of active incidents
- Overall active workload

For example, an engineer with two active P1 incidents may have a heavier workload than an engineer with four low-priority incidents.

The system should calculate a normalized workload score.

Generally:

Lower effective workload = Higher workload-balance score.

This prevents the system from repeatedly assigning incidents to already overloaded engineers.

---

# 13. Idle Time and Assignment Fairness

The assignment algorithm should also consider how long an eligible engineer has been without receiving a new assignment.

This prevents a situation where one engineer continuously receives incidents while another eligible engineer remains idle for a long period.

Example:

Arjun received an assignment 5 minutes ago.

Priya received an assignment 25 minutes ago.

Rahul received an assignment 60 minutes ago.

Rahul should receive a higher fairness score because he has been idle for longer.

However, fairness must not override technical suitability.

An engineer should not receive an incident only because they have been idle longer if another engineer is significantly more suitable for the incident.

Therefore, idle time has a limited weight in the final assignment score.

---

# 14. Assignment Score

The final assignment score is calculated using normalized scores.

The recommended formula is:

Assignment Score =
(Experience Score × 0.40)
+
(Availability Score × 0.25)
+
(Workload Score × 0.20)
+
(Idle/Fairness Score × 0.15)

Example:

Arjun:
Experience = 95
Availability = 100
Workload = 40
Fairness = 20

Priya:
Experience = 80
Availability = 100
Workload = 80
Fairness = 70

Rahul:
Experience = 60
Availability = 100
Workload = 95
Fairness = 90

The system calculates the final score for every eligible engineer.

The engineer with the highest final score is assigned the incident.

The above numbers are only an example. The actual score is calculated dynamically using the current incident and current engineer information.

---

# 15. Assignment Principle

The assignment algorithm should not always select:

- The most experienced engineer
- The least busy engineer
- The engineer who has been idle the longest

Instead, it should select the best overall candidate.

The goal is:

Assign the incident to the most suitable available support engineer while maintaining reasonable workload balance and fairness.

The assignment process should prioritize capability while preventing workload concentration.

---

# 16. Incident Lifecycle

Every incident follows these statuses:

1. REPORTED
2. ASSIGNED
3. IN PROGRESS
4. ROOT CAUSE IDENTIFIED
5. RESOLUTION IN PROGRESS
6. RESOLVED

The USER can track the current status of their incident.

The SUPPORT engineer updates the status as the incident progresses.

---

# 17. REPORTED Status

REPORTED means the USER has successfully submitted the incident.

At this stage:

- Incident has been created
- Incident information is available
- Classification has been confirmed
- Priority has been determined
- Assignment process can begin

---

# 18. ASSIGNED Status

ASSIGNED means the system has successfully assigned the incident to an eligible support engineer.

The assigned SUPPORT engineer can now view the incident in their dashboard.

The USER can see that the incident has been assigned.

---

# 19. IN PROGRESS Status

IN PROGRESS means the SUPPORT engineer has started investigating the incident.

The SUPPORT engineer can:

- Communicate with the USER
- Investigate the issue
- Review incident information
- Use OpsAI
- Search similar historical incidents
- Analyze the incident

The USER can see that investigation has started.

---

# 20. ROOT CAUSE IDENTIFIED Status

ROOT CAUSE IDENTIFIED means the SUPPORT engineer has identified and confirmed the technical root cause.

OpsAI may suggest a possible root cause, but SUPPORT must make the final decision.

The status should only move to ROOT CAUSE IDENTIFIED after SUPPORT confirms the root cause.

---

# 21. RESOLUTION IN PROGRESS Status

RESOLUTION IN PROGRESS means SUPPORT is implementing, applying, or verifying the solution.

During this stage, SUPPORT can:

- Apply the recommended fix
- Perform required investigation
- Verify the system
- Test the solution
- Communicate progress with the USER

---

# 22. RESOLVED Status

RESOLVED means the issue has been fixed and the solution has been verified.

SUPPORT records:

- Final root cause
- Resolution details

The USER receives a real-time update that the incident has been resolved.

The incident then becomes part of the historical incident records.

---

# 23. Incident Tracking

The USER should always be able to see the current state of their incident.

The incident page should display:

- Incident ID
- Title
- Service
- Category
- Severity
- Priority
- Current status
- Assigned support engineer
- Conversation
- Root cause when available
- Resolution when available

Status changes should be reflected in real time.

---

# 24. Incident-Specific Conversation

Chat is not a separate global feature.

Every incident has its own continuous conversation.

The fundamental rule is:

ONE RESOLVE_INCIDENT = ONE CONTINUOUS CONVERSATION

For example:

INC-1024 — Payment Failure

The conversation may contain:

USER:
Payment is failing again.

SUPPORT:
I'm checking the issue now.

USER:
It happens after clicking Pay.

SUPPORT:
Got it. I'm investigating.

All messages belong to the conversation of INC-1024.

A different incident, such as INC-1031, has its own separate conversation.

---

# 25. Conversation Features

The USER can:

- Send messages
- Receive support replies
- View previous messages
- View complete conversation history
- Receive messages instantly
- See message read status

The SUPPORT user can:

- Send messages
- Receive user replies
- View previous messages
- View complete conversation history
- Receive messages instantly
- See message read status

The conversation remains associated with the incident throughout its lifecycle.

---

# 26. Real-Time Chat

The chat must work in real time.

When the USER sends a message, SUPPORT should receive it without manually refreshing the page.

When SUPPORT sends a message, the USER should receive it without manually refreshing the page.

The same real-time behavior applies to important incident status updates.

Examples of real-time events include:

- New message
- Message read
- Incident assigned
- Incident status changed
- Root cause identified
- Resolution started
- Incident resolved

---

# 27. Message Read Status

The conversation supports read tracking.

When a user sends a message, it can initially be considered unread by the recipient.

When the recipient views the message, the message becomes read.

The UI can indicate whether a message has been read.

This functionality applies to both USER and SUPPORT.

---

# 28. Conversation History

The complete conversation remains available for the entire lifecycle of the incident.

Example:

USER:
Payment isn't working.

SUPPORT:
Checking.

USER:
Here is the error.

SUPPORT:
Issue identified.

USER:
Thank you.

All these messages remain part of the incident history.

Even after the incident is resolved, the conversation remains available when authorized users view the incident.

---

# 29. Conversation and Historical Intelligence

The incident conversation is an important source of information for OpsAI.

When a support engineer investigates an incident, OpsAI can use:

- Current incident information
- Incident description
- Classification
- Severity
- Priority
- Conversation history
- Historical incidents
- Previous root causes
- Previous resolutions

This allows OpsAI to provide more context-aware assistance.

---

# 30. SUPPORT Dashboard

The SUPPORT dashboard provides an overview of the support engineer's workload and assigned incidents.

The dashboard should display:

- Total assigned incidents
- Currently open incidents
- Resolved incidents
- Average resolution time
- My incidents
- High-priority incidents
- Current workload information

Example incidents:

INC-1024 — Payment Failure

INC-1031 — Login Failure

INC-1042 — Notification Delay

The SUPPORT engineer can open an incident to investigate it.

---

# 31. SUPPORT Incident View

When SUPPORT opens an incident, the incident page should provide all relevant information in one place.

It should contain:

- Incident ID
- Title
- Description
- Service
- Category
- Severity
- Priority
- Current status
- User information according to authorization
- Conversation
- Status history
- OpsAI features
- Root cause
- Resolution

The purpose is to avoid making SUPPORT navigate through multiple unrelated pages to understand one incident.

---

# 32. OpsAI

OpsAI is an AI assistant available to SUPPORT inside the incident.

OpsAI helps SUPPORT investigate incidents more efficiently.

OpsAI provides assistance for:

1. Summarizing the conversation
2. Finding similar historical incidents
3. Analyzing the incident
4. Suggesting possible root causes
5. Suggesting resolution steps

OpsAI is an assistant, not the final decision-maker.

SUPPORT always makes the final technical decision.

---

# 33. OpsAI Conversation Summary

SUPPORT can select:

Summarize Conversation

OpsAI reads the relevant conversation and generates a concise summary.

For example, if an incident contains 50 messages, OpsAI can convert them into a short summary.

Example:

User reported payment failures beginning at 10:35 AM. Three attempts failed. Support identified possible database connectivity issues and is currently investigating.

The purpose is to allow SUPPORT to understand a long conversation quickly without manually reading every message.

---

# 34. OpsAI Similar Incident Search

SUPPORT can ask:

Have we seen this before?

OpsAI searches historical incidents for similar incidents.

Example:

INC-0812 — 92% similarity

INC-0742 — 87% similarity

INC-0651 — 81% similarity

Similarity can consider:

- Service
- Category
- Title
- Description
- Severity
- Conversation content
- Root cause
- Resolution

The SUPPORT engineer can open or inspect relevant historical incidents according to authorization.

---

# 35. Historical Incident Intelligence

Historical incidents are valuable for solving new incidents.

OpsAI should be able to identify patterns across previous incidents.

For example:

Several previous payment incidents may have the same root cause.

This allows SUPPORT to understand whether the current incident may be part of a recurring operational problem.

Historical incidents can provide:

- Previous root causes
- Previous resolutions
- Similar symptoms
- Similar conversations
- Previous investigation patterns

---

# 36. OpsAI Incident Analysis

SUPPORT can request:

Analyze Incident

OpsAI analyzes the current incident using the available incident information and historical context.

The analysis can provide:

- Incident summary
- Important evidence
- Possible causes
- Similar historical incidents
- Investigation areas
- Recommended next steps

The analysis is advisory.

SUPPORT decides what actions to take.

---

# 37. OpsAI Root Cause Suggestion

SUPPORT can ask:

What could be the root cause?

OpsAI can provide a possible root cause with a confidence score and supporting evidence.

Example:

Possible Root Cause:
Database Connection Exhaustion

Confidence:
82%

Evidence:
Three similar historical incidents had the same root cause.

The confidence value is an AI estimate and should not be treated as absolute certainty.

SUPPORT must review the evidence and make the final decision.

---

# 38. Root Cause Confirmation

The final root cause is confirmed by SUPPORT.

The process is:

OpsAI suggests possible root cause.

SUPPORT reviews the suggestion.

SUPPORT investigates the issue.

SUPPORT confirms the actual root cause.

The incident can then move to:

ROOT CAUSE IDENTIFIED

OpsAI must never automatically finalize the root cause without SUPPORT confirmation.

---

# 39. OpsAI Resolution Recommendation

SUPPORT can ask:

What should I do?

OpsAI can provide recommended investigation or resolution steps.

Example:

1. Check active database connections.
2. Check the connection pool.
3. Compare current usage with peak usage.
4. Increase the pool if necessary.
5. Verify payment transactions.

These are recommendations only.

SUPPORT decides whether to follow them.

AI must not automatically execute a resolution or mark the incident as resolved.

---

# 40. Resolution Management

Once the root cause has been identified, SUPPORT works on the resolution.

The resolution process includes:

- Applying the fix
- Verifying the fix
- Testing the affected functionality
- Confirming the issue no longer occurs
- Recording the resolution details

Example:

Root Cause:
Database connection exhaustion.

Resolution:
Database connection pool was increased and the payment service was restarted. Payment transactions were verified successfully after the fix.

After successful verification, SUPPORT can mark the incident as RESOLVED.

---

# 41. Real-Time Incident Updates

The USER should receive real-time updates for important incident events.

Examples:

- Incident assigned
- Investigation started
- New support message
- Status changed
- Root cause identified
- Resolution started
- Incident resolved

The USER should not have to manually refresh the page to see these updates.

---

# 42. User Resolution View

After an incident is resolved, the USER can view:

- Final status
- Root cause
- Resolution details
- Complete conversation
- Incident history

Example:

Incident:
INC-1024

Status:
RESOLVED

Root Cause:
Database connection exhaustion.

Resolution:
Connection pool was increased and payment transactions were verified successfully.

---

# 43. Incident History

Every incident maintains its complete history.

Incident history includes:

- Original incident information
- Classification
- Priority
- Assignment
- Status changes
- Conversation
- Root cause
- Resolution
- Relevant investigation information

Resolved incidents remain available as historical incidents.

Historical incidents can later be used by OpsAI for similar-incident analysis.

---

# 44. Recurring Incident Intelligence

ResolveIT should be capable of identifying recurring operational problems through historical incidents.

For example:

Previous incidents:

INC-0651 — Payment Failure

INC-0742 — Payment Failure

INC-0812 — Payment Failure

INC-1024 — Payment Failure

If the incidents contain similar symptoms and root causes, OpsAI can identify the recurring pattern.

This helps SUPPORT understand whether the current problem is a repeated operational issue.

---

# 45. Support Analytics

SUPPORT can view analytics related to incident handling.

Personal analytics may include:

- Total assigned incidents
- Currently open incidents
- Resolved incidents
- Average resolution time
- Most common issue
- Recurring incidents
- Current workload

Broader analytics may include:

- Incidents by service
- Incidents by category
- Incidents by severity
- Incidents by priority
- Common root causes
- Average resolution time
- Recurring problems
- Support workload
- Incident trends

---

# 46. Access and Authorization

The system must maintain role-based access.

USER should only access:

- Their own incidents
- Their own incident conversations
- Their own incident history

SUPPORT should only access:

- Incidents they are authorized to handle
- Conversations associated with those incidents
- Relevant historical information allowed by the system

A USER must not be able to access another USER's incident.

A USER must not access support-only features.

AI features available to SUPPORT must not automatically be exposed to USER unless explicitly designed as a user feature.

## Session Lifecycle

A session begins at login and ends when the user logs out or the token expires on
its own, whichever comes first.

Logout must take effect immediately and on the server, not merely by the client
discarding its copy of the token. After logout:

- the same token must be refused by every protected REST endpoint, with 401
- the same token must be refused at WebSocket/STOMP connection
- logging in again must issue a working token

Logout applies to the session the user is actually holding. Logging out on one
device must not end that user's sessions on other devices.

A user can only log themselves out. There is no facility for one user to end
another user's session.

---

# 47. Core Business Rules

1. ResolveIT has exactly two roles: USER and SUPPORT.

2. USER can report incidents.

3. USER provides the incident title and description.

4. OpsAI assists with Service, Category, and Severity classification.

5. AI classification is a recommendation.

6. USER reviews the AI classification before submitting the incident.

7. USER can accept or modify the suggested Service, Category, and Severity.

8. USER does not choose the support team.

9. USER does not choose the support engineer.

10. Priority is determined by the system.

11. Support team identification is automatic.

12. Support engineer assignment is automatic.

13. Assignment considers similar incident experience, availability, workload, and idle time/fairness.

14. The assignment algorithm prioritizes suitability while maintaining workload balance.

15. Every incident has its own continuous conversation.

16. Chat is part of the incident and is not a separate global conversation.

17. Every message belongs to an incident conversation.

18. USER and SUPPORT can communicate through the incident conversation.

19. Messages must support real-time delivery.

20. Messages should support read status.

21. Complete conversation history remains associated with the incident.

22. USER can track incident status in real time.

23. SUPPORT is responsible for investigation.

24. OpsAI assists SUPPORT during investigation.

25. OpsAI can summarize conversations.

26. OpsAI can find similar historical incidents.

27. OpsAI can analyze incidents.

28. OpsAI can suggest possible root causes.

29. OpsAI can suggest resolution steps.

30. OpsAI does not make the final technical decision.

31. SUPPORT confirms the final root cause.

32. SUPPORT records the final resolution.

33. SUPPORT verifies the resolution before resolving the incident.

34. Resolved incidents remain available as historical records.

35. Historical incidents can be used for future similar-incident analysis.

36. Historical conversations can contribute to incident intelligence.

37. USER can view the final root cause and resolution after the incident is resolved.

38. Access to incidents and conversations must respect user authorization.

39. Logout must revoke the session server-side and take effect immediately, across both REST and real-time channels.

40. A user can end only their own session, and only the session they are currently holding.

---

# 48. Complete Feature Set

ResolveIT includes the following major features:

1. User Registration
2. User Login
3. Support Login
4. User Dashboard
5. Support Dashboard
6. Incident Reporting
7. AI-Assisted Incident Classification
8. Classification Review
9. Service Selection/Confirmation
10. Category Selection/Confirmation
11. Severity Selection/Confirmation
12. Automatic Priority Determination
13. Automatic Support Team Identification
14. Intelligent Support Engineer Assignment
15. Similar Incident Experience Scoring
16. Availability Scoring
17. Workload Balancing
18. Idle Time/Fairness Scoring
19. Incident Status Tracking
20. Incident Details
21. Incident-Specific Conversation
22. Real-Time Messaging
23. Message Read Status
24. Complete Conversation History
25. Support Investigation
26. OpsAI Conversation Summary
27. OpsAI Similar Incident Search
28. OpsAI Incident Analysis
29. OpsAI Root Cause Suggestion
30. Root Cause Confirmation
31. OpsAI Resolution Recommendation
32. Resolution Management
33. Real-Time Status Updates
34. Incident Resolution
35. Incident History
36. Historical Incident Intelligence
37. Recurring Incident Detection
38. Support Workload Analytics
39. Incident Analytics
40. Root Cause Analytics
41. Resolution Time Analytics
42. Role-Based Access
43. Direct Logout with Immediate Token Revocation

---

# 49. Final Product Behavior

ResolveIT should behave as an incident-centric support platform.

A USER reports an incident by providing a title and description.

OpsAI assists in classifying the incident by suggesting the service, category, and severity.

The USER reviews and confirms or modifies the classification.

The system determines the appropriate priority and automatically identifies the relevant support team.

The system then evaluates eligible support engineers using:

- Similar incident experience
- Availability
- Current workload
- Idle time/fairness

The engineer with the highest overall assignment score receives the incident.

The USER can track the incident while SUPPORT investigates it.

Every incident has its own continuous real-time conversation.

The USER and SUPPORT can communicate through this conversation throughout the incident lifecycle.

The conversation is not a separate feature. It is an integral part of the incident.

SUPPORT can use OpsAI to:

- Summarize the conversation
- Find similar historical incidents
- Analyze the incident
- Identify possible root causes
- Receive resolution recommendations

OpsAI assists SUPPORT but does not make the final technical decision.

SUPPORT confirms the root cause, performs the resolution, verifies the fix, records the resolution, and marks the incident as resolved.

The USER receives real-time updates throughout the lifecycle.

After resolution, the complete incident, conversation, root cause, and resolution remain part of the incident history.

Historical incidents can then be used by OpsAI to identify similar incidents, recurring problems, previous root causes, and previous resolutions.

The central concept of ResolveIT is:

Every incident is a complete operational record containing the incident details, classification, assignment, conversation, investigation, root cause, resolution, and historical intelligence.