---
version: 1
slug: "assistance-operit-ui-main-plannerpages-kt-ebc64f29"
primary_target: "app/src/main/java/com/ai/assistance/operit/ui/main/PlannerPages.kt"
related_targets: ["app/src/main/java/com/ai/assistance/operit/ui/main/PlannerCalendarUi.kt"]
---

# 日程：安静的每日计划

Mode: Operate. In-place extension of the native Material 3 app. Code-led; no approved comp. Preserve the app theme, fonts, role-space isolation, annual anniversaries, task completion, reminders and private-by-default records.

## THESIS
Open the planner and understand one day immediately: select a date, scan actual times, add an arrangement.

## OWN-WORLD
星语 uses its existing sage Material theme here. A calm calendar and a precise time gutter give this utility its character; the pixel farm and mascot artwork stay on their own surfaces.

## STORY
The header identifies the role. Four categories retain their current meaning. Week/month selection leads to the day's agenda; an all-records view keeps old and future entries discoverable. Tasks retain overdue/today/later/completed groups. Sensitive cycles remain actual records.

## FIRST VIEWPORT
Phone: compact title/actions, categories, month controls and a seven-day strip, followed by the selected day's timeline. Wide displays place a full month calendar beside the agenda. A small add action remains reachable. Empty states invite a real action without sample entries.

## FORM
Use native Compose controls and Material theme colors. Calendar days and icon actions have 48dp targets; dates expose full spoken labels and selection. Use dots for real record presence and icons for sharing/reminders. Flat timeline rows and generous section spacing replace repeated elevated forms. Date/time selection uses native pickers. At enlarged font sizes, stack layouts and allow scrolling.

## FINISH
Research: Structured visual planning https://structured.app/visual-planning and TickTick official calendar features https://ticktick.com/features?language=zh_cn. These are interaction references, not copied assets. Verify date boundaries, time zones, annual leap dates and overdue tasks with JVM checks. Capture the installed native page on phone, large text, landscape and tablet, plus populated agenda and editor. Preserve all existing user records; verification entries are clearly marked and removed through the same app. No HTML/CSS detector applies. Document actual verification and any limits.
