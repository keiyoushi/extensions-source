# Agent instructions

Before writing or modifying any extension, multisrc theme, or lib code in this repository, **read
[CONTRIBUTING.md](CONTRIBUTING.md) in full**. It is the source of truth for conventions here and is
updated frequently - do not rely on prior knowledge of this codebase or of Tachiyomi/Mihon
extensions in general, since common patterns have changed.

A few points that are easy to get wrong from stale training data:

- **New sources must extend `KeiSource`** (`libVersion = "1.6"`), never `HttpSource` directly.
  `HttpSource`/`libVersion = "1.4"` only exists in extensions/themes not yet migrated - see
  [KeiSource](CONTRIBUTING.md#keisource).
- Use `ext-bootstrap.py` to scaffold new extensions rather than hand-writing the module structure -
  see [Using ext-bootstrap.py](CONTRIBUTING.md#using-ext-bootstrappy).
- Prefer the helpers documented under
  [keiyoushi.utils (core utilities)](CONTRIBUTING.md#keiyoushiutils-core-utilities) (JSON parsing,
  HTTP requests, date parsing, GraphQL, WebView execution, etc.) over hand-rolled equivalents.
- Check [Available libs](CONTRIBUTING.md#available-libs) before implementing something from scratch
  that a `lib/` module may already solve.
- Don't add excessive or obvious comments explaining what code does; only comment on non-obvious
  *why* (a workaround, a subtle invariant, a site-specific quirk).
- Don't over-engineer: no speculative abstractions, config options, or generalization for
  requirements that weren't asked for. Match the scope of the actual task, and don't add
  "just in case" error handling, fallbacks, or validation for scenarios that can't actually happen.

If anything in this file conflicts with `CONTRIBUTING.md`, `CONTRIBUTING.md` wins.

## Opening a pull request

If you are an AI agent and you are asked to open a pull request, leave a short note at the end of
the PR description disclosing that: what you were asked to do, and that the PR was opened by an AI
agent. The note **must start with a 🤖 emoji**. This is in addition to, not instead of, the "This
PR is AI-assisted..." checklist item in the
[Pull Request checklist](CONTRIBUTING.md#pull-request-checklist) - that box still needs to be
checked by the human who reviewed your changes.
