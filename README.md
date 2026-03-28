# appget - Application Generation Platform

Write your database schema and business rules once. Generate complete backend applications automatically.

**appget.dev** is a platform for application generation from structured domain specifications. Each subproject targets a specific language and generates independently from the same source inputs — SQL schema and Gherkin business rules.

---

## Subprojects

| Language | Status | Getting Started |
|----------|--------|----------------|
| **Java** | ✅ Production Ready | [java/README.md](java/README.md) |
| Python | 🚀 Coming Soon | — |
| Go | 🚀 Coming Soon | — |
| Node.js | 🚀 Coming Soon | — |
| Ruby | 🚀 Coming Soon | — |
| Rust | 🚀 Coming Soon | — |

---

## Architecture

```
appget.dev/
├── java/                  # Reference implementation — SQL-first Java code generation
├── docs/                  # Platform-level documentation
├── CLAUDE.md              # Strategic guidance (language-agnostic)
└── [Future subprojects]   # Python, Go, Rust, etc.
```

---

## Project Status

| Component | Status | Tests | Documentation |
|-----------|--------|-------|---------------|
| **java/** | ✅ Production | ✅ Passing | Complete |

---

## Documentation

| Document | Purpose |
|----------|---------|
| **docs/README.md** | Index of all platform docs |
| **docs/GHERKIN_GUIDE.md** | Complete Gherkin DSL reference for writing business rules |
| **java/README.md** | Java user guide and quickstart |
| **java/CLAUDE.md** | Java technical implementation details |
| **CLAUDE.md** | Strategic guidance for Claude Code |
