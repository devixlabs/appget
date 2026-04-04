# CLAUDE.md - appget.dev Code Generation Platform

This file provides strategic guidance to Claude Code when working on the appget.dev project.

---

## Project Overview

**appget.dev** is a DevixLabs platform for application generation from structured domain specifications. The project is organized into **language-specific subprojects**, each with its own complete code generation pipeline.

### Architecture

```
appget.dev/
├── java/                          # Java code generation system
│   ├── README.md                  # User guide and quick start
│   ├── CLAUDE.md                  # Technical implementation guide
│   ├── PIPELINE.md                # Detailed pipeline architecture
│   ├── schema.sql                 # SQL source of truth
│   ├── features/                  # Gherkin business rules
│   ├── metadata.yaml              # Metadata registry (14 categories, toggle model)
│   └── src/                       # Java generators and runtime
│
└── [Future subprojects...]        # Python, Go, Rust, etc.
```

---

## Subproject Structure

Each subproject is **self-contained** with:
- Complete documentation in its own directory
- Independent build system
- Language-specific tooling
- Full test coverage

### Current Subproject: Java

**Status**: ✅ Production Ready

**What it generates**:
- Protocol Buffer models from SQL schema
- Business rule specifications from Gherkin `.feature` files
- Spring Boot REST API servers
- HTML template files + PageRenderer classes (content negotiation)
- gRPC service stubs
- OpenAPI 3.0 specifications

**See**: [java/README.md](java/README.md) for user guide and [java/CLAUDE.md](java/CLAUDE.md) for technical details.

---

## Design Principles

### 0. Build-Time Code Generation Over Runtime Framework Features
Where behavior can be determined at build time and the security surface is small enough to audit and fuzz-test exhaustively, generate the code directly rather than depending on framework libraries. Reserve framework dependencies for infrastructure concerns (HTTP serving, TLS, connection pooling) that are genuinely hard to implement correctly and where the security surface is too large to own.

**Rationale**: Framework-specific libraries (templating engines, format converters, middleware) introduce version coupling, CVE exposure, and lock each generated server to a single ecosystem. Generated code with centralized security utilities (e.g., `HtmlEscapeUtils`) is auditable in seconds, fuzz-testable, and portable across all target languages without upstream dependencies. LLMs can generate and maintain this code more reliably than tracking framework version schedules.

**Applies to**: HTML rendering (PageRenderer + templates, not Thymeleaf/Jinja2), content transformation, output formatting. **Does NOT apply to**: HTTP serving, TLS, connection pooling, database drivers — use framework/library for these.

### 1. Language-First Code Generation
Java is the **reference implementation**. Design patterns in java/ set the structural template that all future language subprojects (Go, Python, Rust) replicate. When making architecture decisions, optimize for cross-language structural consistency — not just Java's immediate needs.

Each subproject generates code for a specific language:
- **java/**: Protobuf models, Java specifications, Spring Boot servers
- **[Future]**: Python Django, Go gRPC, Rust Actix, etc.

No cross-language dependencies. Each generates independently.

### 2. Schema-First, Rule-First Design
- **SQL schema** (schema.sql) = source of truth for domain models
- **Gherkin features** (features/*.feature) = source of truth for business rules
- Generated code is **disposable** — regenerate from sources, never edit

### 3. Descriptor-Based Runtime
Runtime evaluation uses protobuf descriptors (dynamic model inspection) rather than hard-coded class lists. Enables:
- Generic rule evaluation across any model
- Metadata-aware authorization
- Compound AND/OR conditions
- View-targeted rules

### 4. Comprehensive Testing
Every subproject includes:
- Comprehensive test coverage (see `src/test/` folder)
- Multi-stage test suites (parsing, generation, evaluation)
- All tests must pass before deployment

### 5. Portable Code Generation Strategy
Generators use **plain string building** (StringBuilder) as the default approach. This is intentional — every target language (Go, Ruby, Python) has string concatenation, so the pattern ports directly without hunting for templating library equivalents.

**Handlebars (.hbs) templates** are used selectively in the Java subproject for two generators where the output is mostly structural with variable slots:
- `DescriptorRegistryGenerator` → `templates/descriptor/DescriptorRegistry.java.hbs`
- `SpecificationGenerator` → `templates/specification/*.java.hbs`

The remaining generators (`AppServerGenerator`, `ProtoOpenAPIGenerator`, `ModelsToProtoConverter`, etc.) intentionally stay with StringBuilder — their output involves complex conditional logic that would be harder to maintain in a template.

**Rule for future subprojects**: Use plain string building. Do not introduce templating libraries unless the generated output is simple and structural.

### 6. Language-Specific Utility Classes
Each subproject provides a language-specific utility class (`JavaUtils.java`, future `GoUtils.go`, `RustUtils.rs`, etc.) that handles naming convention transforms from the canonical snake_case intermediates to the target language's conventions. A separate `CodeGenUtils` class holds language-agnostic string operations shared by all generators. Generators read snake_case from `models.yaml`/`specs.yaml` and call the language utility at codegen time — never store language-specific casing in intermediate files.

### 7. Git-Friendly Artifacts
Only source files are committed:
- ✅ schema.sql, features/*.feature, metadata.yaml (sources)
- ✅ src/main/java/, src/test/ (handwritten code)
- ✅ build.gradle, Makefile (build config)
- ❌ models.yaml, specs.yaml (generated intermediates)
- ❌ src/main/java-generated/ (generated code)
- ❌ build/ directory (artifacts)
- ❌ generated-html/ (generated static HTML pages — pipeline artifact)
- ❌ templates/ (generated HTML templates — pipeline artifact)

---

## Development Workflow

### For Java Subproject

```bash
cd java

# View available commands
make help

# Full pipeline (recommended)
make all              # clean → generate → test → build (no server needed)

# Individual steps
make features-to-specs   # .feature files + metadata → specs.yaml
make parse-schema        # schema.sql → models.yaml
make generate            # protobuf + specifications + OpenAPI
make test                # unit tests
make generate-server     # Generate REST API server
make generate-html       # Generate static HTML CRUD pages
make run-server          # Start server on http://localhost:8080
make verify              # All server-dependent tests (requires server running)
```

**See**: [java/README.md](java/README.md) for complete user guide

### Adding Features

1. **New models**: Edit `schema.sql` in java/
2. **New rules**: Edit `features/*.feature` in java/ (see [docs/GHERKIN_GUIDE.md](docs/GHERKIN_GUIDE.md) for the complete DSL reference)
3. **New authorization**: Edit `metadata.yaml` in java/
   - Enable built-in categories with `enabled: true`; only enabled categories flow through pipeline
4. **Run pipeline**: `make all` in java/

### Makefile Convention

`Makefile.example` at the project root is the language-agnostic template. All subproject Makefiles follow the same target names and structure. Key rule: `make all` never requires a running server; `make verify` is the umbrella for all server-dependent tests. Each language subproject delegates to `scripts/*.sh` for implementation.

---

## Gherkin DSL Validation Rules

When writing `.feature` files or abstract rule examples, validate:

1. **Every rule must have ≥1 non-metadata condition** (maps to `When` clause in Gherkin). Metadata-only rules are invalid and cause spec generation errors.
   - ❌ Bad: `METADATA: sso.authenticated; CONDITIONS: (metadata) authenticated equals true`
   - ✅ Good: `METADATA: sso.authenticated; CONDITIONS: (metadata) authenticated equals true AND content (TEXT) does not equal ""`

2. **Name collisions**: New rule names (especially in skills like SKILL.md) must be checked against existing names in appget-feature-dsl skill to avoid conflicts during spec generation.

3. **Metadata category references** (in features/*.feature or metadata.yaml): All referenced categories must exist in metadata.yaml with `enabled: true`. Unknown or disabled categories cause pipeline errors with explicit "unknown category" or "disabled category" messages.

---

## Session Recovery via Logs

If Claude Code is interrupted (power loss, timeout):
- Session logs are stored at: `~/.claude/projects/<project-id>/*.jsonl`
- Search logs with: `grep "<pattern>" ~/.claude/projects/<project-id>/*.jsonl`
- Each log contains the full conversation history and can reveal exactly where the session left off
- Check for "quality review", "pitfall", or specific file names to reconstruct context

---

## Documentation Navigation

| Document | Purpose |
|----------|---------|
| **docs/README.md** | Index of all platform docs — active reference and pending work |
| **docs/GHERKIN_GUIDE.md** | Complete Gherkin DSL reference — keywords, operators, patterns, and full University domain examples for writing `.feature` files |
| **java/README.md** | User guide, quickstart, examples |
| **java/CLAUDE.md** | Technical implementation, build system, generators |
| **java/PIPELINE.md** | Detailed pipeline architecture, type mappings |
| **This file** | Strategic guidance, subproject navigation |

---

## docs/ Document Lifecycle

### Two-Tier Structure

```
docs/
  *.md              Active — implemented, authoritative reference
  todos/
    *.md            Pending — specs not yet implemented, future work, tracked gaps
```

**Rule**: `docs/` root contains only docs that describe **currently implemented behavior**. Everything else lives in `docs/todos/`.

### Lifecycle States

A doc progresses through these states:

| State | Location | Meaning |
|-------|----------|---------|
| **Pending** | `docs/todos/` | Spec or plan for future work. May be a full spec draft, a gap tracker, or a roadmap. Not yet implemented in any subproject. |
| **Active** | `docs/` | Describes behavior that is implemented and verified in at least one subproject (currently Java). Authoritative reference for all implementations. |
| **Archived** | `docs/archive/` | Superseded or no longer relevant. Kept for historical context only. Create this directory when the first doc is archived. |

### Promotion Rules (Pending -> Active)

A doc moves from `docs/todos/` to `docs/` when **all** of these are true:

1. The behavior it describes is **implemented in at least one subproject** and verified by tests
2. The doc has been **reviewed against the source code** (not just written speculatively)
3. Any remaining gaps are **extracted to `docs/todos/CONTRACT_GAPS.md`** with a GAP-ID

### Demotion Rules (Active -> Pending)

A doc moves back to `docs/todos/` when:
- A source-code audit reveals that the majority of described behavior is **not implemented**
- The doc was promoted prematurely

### Gap Tracking

Minor gaps in Active docs (missing fields, unimplemented operators, etc.) are tracked in `docs/todos/CONTRACT_GAPS.md` rather than demoting the entire doc. Each gap has:
- A stable ID (e.g., `GAP-R1`)
- The source doc it belongs to
- The fix location in source code
- Effort estimate

When a gap is resolved: delete the entry from `CONTRACT_GAPS.md`. When all gaps for a doc are resolved, note it in the commit message.

### When Adding a New Doc

1. **Is the behavior implemented?** Write it in `docs/`, verify against source code
2. **Is it a spec for future work?** Write it in `docs/todos/`
3. **Is it a plan or roadmap?** Write it in `docs/todos/`
4. **Update `docs/README.md`** — add the doc to the correct section (Active or Pending)

### When Auditing Docs

Periodic audits should verify that Active docs still match source code. Run this checklist:
1. For each Active doc, confirm the described behavior exists in source
2. New gaps go into `CONTRACT_GAPS.md`
3. Docs that are >50% unimplemented move to `docs/todos/`
4. Resolved gaps get deleted from `CONTRACT_GAPS.md`

---

## Pending Work & TODO Awareness

All pending work is tracked in two systems. Every session and sub-agent should know about both.

| System | Entry Point | Slash Command | Purpose |
|--------|-------------|---------------|---------|
| **Spec docs** | `docs/todos/ROADMAP.md` | `/audit-todos-dlabs` | What to build — phase-mapped specs, gap tracking, lifecycle management |
| **Execution tasks** | `TODO*.md` in working directory | `/todo-breakdown` | How to build it — recursive task decomposition, atomic leaf execution |

**Workflow**: ROADMAP.md defines phases and links to spec docs. When starting a phase, `/todo-breakdown` decomposes it into executable TODO files. After completing work, `/audit-todos-dlabs` audits specs against the codebase and cleans up finished docs.

---

## Key Principles for Claude Code

1. **Understand the subproject first** - Read the README in the subproject directory
2. **Check the Makefile** - Use provided make commands for builds
3. **Never edit generated code** - Modify sources (schema.sql, features/), regenerate
4. **Run tests after changes** - `make test` verifies entire pipeline
5. **Keep root docs abstracted** - Language-agnostic info only; details go in subproject CLAUDE.md

---

## Resources

- **Java Subproject**: [java/README.md](java/README.md), [java/CLAUDE.md](java/CLAUDE.md), [java/PIPELINE.md](java/PIPELINE.md)
- **Root README**: [README.md](README.md) - Project overview
- **Parent CLAUDE.md**: [../CLAUDE.md](../CLAUDE.md) - DevixLabs organizational guidance

---

**Last Updated**: 2026-04-03
**Status**: Language-agnostic guidance (Java details in java/CLAUDE.md)
