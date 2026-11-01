# Horizon Issues (Android)

A small Android app for contributors: the open issues of `horizon-rust/horizon` at a glance, with who holds each one
and the pull request that fixes it, so you can pick something free before you `/claim` it
([CONTRIBUTING.md](../../CONTRIBUTING.md#claim-before-you-start)). It is a dev tool like the rest of `tools/`, not
part of the game. Kotlin and Jetpack Compose; the font is Horizon's own Geist from `crates/horizon-theme/fonts`.

## What it shows

- **Overview**: how many open issues are *Available*, *Claimed* or *Has PR*, as a bar and three tiles that filter.
- **The list**: search by title, `#number`, `@user` or label; filter by status or label; sort by newest, recently
  updated, most discussed or oldest. Each row has a status pill (`Available`, `@login · 2d left`, `PR #123`,
  `Draft #123`), labels, assignees and comment count. Pull to refresh; the last list is kept for offline starts.
- **Open / Fixed**: the toggle beside the title switches to the issues recently closed as fixed (the last 300
  closed issues GitHub touched, keeping those closed as completed), most recently closed first, each with the merged
  pull request that closed it, and how many were fixed in the last day, week and month.
- **Pins and groups**: long-press a row, or use the pin and bookmark buttons on an issue. Pinned issues stay at the
  top of the list; groups are named collections (*To try*, *Rendering*...) shown as chips beside the filters, each
  opening its issues from both the open and fixed lists. They live on the phone, per repository.
- **An issue**: its status card (who claimed it and when, and the 5-day release clock of a `/claim` made without a
  pull request), its linked pull requests (open, draft, merged, closed; whether they close or mention it), the
  description and comments in Markdown, with images and videos shown in place, also the Discord attachments
  Kira copies in as links (an expired Discord link turns into a row pointing at the thread). `/claim` comments (also `🤖 Agent: /claim`) are badged by what came of them:
  *claim* for the one its author holds, *refused* when the workflow answered with a refusal, *claim · over* when it
  was released or given back. With a token, *Claim this issue* posts
  `/claim` for you.

The statuses follow `tools/ci/claim_command.py`:

| Status | Rule |
|---|---|
| Available | No assignee, no `in progress` label (Kira's Discord claim), no open pull request linking it |
| Claimed | An assignee (a `/claim`, or by hand) or the `in progress` label. A `/claim` (assigned by the workflow) is released after 5 days without an open linked pull request; assignments by hand never are |
| Has PR | An open or draft pull request links it: `Fixes #N`, the sidebar, a mention, or its number in the branch name |
| Fixed | Closed as completed (GitHub's state reason; issues closed before it existed count too) |

## Data

Without a token the app uses GitHub's REST API (60 requests an hour; a refresh costs about 7, and unchanged pages
come back as free 304s from the HTTP cache). It finds the pull requests of an issue in the text of the open and
recently closed pull requests, then in the issue's timeline when you open it. With a token (Settings) it reads the
list through GraphQL, the same query the claim workflow uses: sidebar links, cross-references and assignment times.
A fine-grained token with *Issues: read and write* on public repositories is enough. The token stays in the app's
private storage (backups are off).

## Build

The APK comes from CI: the `issues-android` workflow uploads `horizon-issues-apk` (a release build signed with the
debug key, so it installs directly). Locally, with JDK 17+, Gradle 8.14 and the Android SDK (API 35):

```sh
cd tools/horizon-issues-android
gradle :app:assembleRelease                # app/build/outputs/apk/release/app-release.apk
gradle :app:installDebug                   # on a connected phone
gradle -PcoreOnly :core:test               # the GitHub layer alone, no Android SDK needed
```

There is no Gradle wrapper: the repository keeps `.jar` files out (`tools/check_public_tree.py`).

| Path | What |
|---|---|
| `core/` | Plain Kotlin: `GitHubClient` (REST and GraphQL), `Linker` (pull request references), `ClaimRules`, models, tests |
| `app/.../ui/IssueListScreen.kt` | Header, overview, search and filters, rows |
| `app/.../ui/IssueDetailScreen.kt` | Status card, pull requests, description, comments, `/claim` |
| `app/.../ui/SettingsScreen.kt` | Repository, token, theme, rate limit |
| `app/.../ui/theme/` | Palette (zinc neutrals, near-black dark mode, one blue accent, tinted status tones) and type scale |
| `app/.../ui/components/` | Pills, label chips, avatars, skeletons, a small Markdown renderer |
