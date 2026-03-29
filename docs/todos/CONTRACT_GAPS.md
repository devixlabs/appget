# Contract Implementation Gaps

Tracked gaps in the cross-language contract docs that live in `docs/`. Each gap references the source doc and the fix location.

---

## All Java Gaps Resolved (2026-03-24)

GAP-M1, GAP-M2, GAP-S1, GAP-R1, GAP-R2, GAP-R3, GAP-D1 — all resolved in the Java reference implementation.

---

## Remaining

### GAP-P1: Non-Java language package options are aspirational
- **Spec**: Defines Go, Python, Ruby, Node package conventions
- **Current**: Only Java `java_package` options are generated
- **Status**: Blocked on Phase 5 (language implementations). No action needed until then.

---

## Summary

| ID | Doc | Status |
|----|-----|--------|
| GAP-P1 | PROTO_CONVENTIONS | Blocked on Phase 5 |
