---
name: bdd-methodology
description: "Load this skill when decomposing product requirements into behavior specifications, running example mapping sessions, translating user stories into Gherkin scenarios, or discussing BDD principles. Covers the Three Amigos process, example mapping, requirement decomposition, behavior specification, acceptance criteria patterns, and the relationship between business rules and automated specifications."
allowed-tools: Read, Glob, Grep
---

# BDD Methodology — From Requirements to Specifications

Behavior-Driven Development (BDD) is a collaborative approach to defining software behavior through concrete examples. This skill covers the principles, techniques, and practices for translating business requirements into precise, testable specifications.

---

## Core BDD Principles

### Specifications, Not Tests

BDD scenarios describe WHAT the system should do, not HOW to test it. The focus is on behavior specification, not test automation:

```
Test mindset:  "I need to verify the login function works"
BDD mindset:   "When a user provides valid credentials, they gain access to their account"
```

### Shared Understanding Over Coverage

The primary value of BDD is the conversation it forces between business, development, and testing. The scenarios are a byproduct of shared understanding — not the goal itself. A team that writes perfect Gherkin but never discusses requirements has missed the point.

### Living Documentation

Feature files should serve as the authoritative, always-current documentation of business rules. If the system behavior changes, the scenarios must change too. Stale scenarios are worse than no scenarios.

### Concrete Examples Over Abstract Requirements

Abstract: "The system should handle expired accounts appropriately."
Concrete: "When a user's account expired 30 days ago, they see a 'Reactivate Account' prompt instead of the login form."

Concrete examples expose edge cases that abstract requirements hide.

---

## The Three Amigos Process

### Participants

| Role | Brings | Asks |
|------|--------|------|
| **Business** (Product Owner, BA, Domain Expert) | Domain knowledge, priorities, acceptance criteria | "What does the user need? What's the business value?" |
| **Development** (Engineer) | Technical feasibility, constraints, implementation options | "How will this work? What are the edge cases? What's hard?" |
| **Testing** (QA) | Risk assessment, negative cases, boundary conditions | "What could go wrong? What are we NOT handling? What's missing?" |

### Session Structure

1. **Present the story** (Business): Explain the requirement and business context
2. **Discuss examples** (All): Walk through concrete scenarios — happy path, errors, edge cases
3. **Capture examples** (All): Write Given/When/Then examples on cards or a whiteboard
4. **Identify unknowns** (All): Flag questions that need research or stakeholder input
5. **Agree on scope** (All): Decide which examples are in scope for this iteration

### Session Guidelines

- **Timebox**: 25-30 minutes per story. If it takes longer, the story is too big — split it.
- **One story at a time**: Don't batch multiple stories into one session.
- **No solutioning**: Focus on WHAT, not HOW. Implementation details come later.
- **Disagreement is valuable**: Different perspectives expose missing requirements.

---

## Example Mapping

A structured technique for decomposing stories into rules and examples.

### The Cards

| Card Color | Represents | Purpose |
|-----------|-----------|---------|
| Yellow | **Story** | The user story or feature being discussed |
| Blue | **Rules** | Business rules that govern the behavior |
| Green | **Examples** | Concrete examples that illustrate each rule |
| Red | **Questions** | Unknowns that need answers before implementation |

### Running a Session

1. Write the **story** on a yellow card at the top
2. Discuss the first **rule** — write it on a blue card
3. For each rule, generate **examples** (green cards) that illustrate it
4. When something is unclear, write a **question** (red card) instead of guessing
5. Repeat for additional rules

### Example: E-Commerce Coupon Feature

```
STORY: Users can apply discount coupons at checkout

RULE: Coupons must be valid
  EXAMPLE: Valid coupon "SAVE10" gives 10% discount
  EXAMPLE: Expired coupon is rejected with message
  EXAMPLE: Non-existent coupon code shows "Invalid coupon"

RULE: One coupon per order
  EXAMPLE: Applying a second coupon replaces the first
  EXAMPLE: Removing a coupon restores original price

RULE: Minimum order for percentage coupons
  EXAMPLE: 10% coupon on $20 order (minimum $25) is rejected
  EXAMPLE: 10% coupon on $30 order (minimum $25) applies

QUESTION: Can coupons stack with existing promotions?
QUESTION: Do coupons apply before or after tax?
```

### When to Stop

- **Too many blue cards**: The story is too big — split it
- **Too many red cards**: Not enough is known — defer the story, do research first
- **Balanced**: 3-5 rules, 2-4 examples per rule, 0-2 questions = good story

---

## Requirement Decomposition

### From Epic to Scenario

```
Epic       → "Users can manage their account"
  Feature  → "Users can update their profile"
    Story  → "User can change their display name"
      Rule → "Display name must be 2-50 characters"
        Example → Given name "A" (1 char), Then rejected
        Example → Given name "Alice" (5 chars), Then accepted
        Example → Given name "A"*51 (51 chars), Then rejected
```

### Identifying the Happy Path

The happy path is the primary success scenario — the most common, expected flow:

1. What is the user trying to accomplish?
2. What inputs do they provide?
3. What is the expected successful outcome?

Always write the happy path first. Then explore variations.

### Identifying Edge Cases

Systematic questions to surface edge cases:

| Category | Questions |
|----------|-----------|
| **Boundaries** | What happens at minimum/maximum values? Zero? Negative? |
| **Empty/Null** | What if the input is empty, null, or missing? |
| **Duplicates** | What if the same action is performed twice? |
| **Timing** | What if it's too early? Too late? Simultaneous? |
| **Permissions** | What if the user doesn't have access? |
| **State** | What if the entity is in an unexpected state? |
| **Dependencies** | What if a required resource doesn't exist? |
| **Concurrency** | What if two users act on the same data? |

### Negative Cases

For every positive scenario, ask: "What should NOT happen?"

```
Positive: "Premium users get free shipping"
Negative: "Free-tier users do NOT get free shipping"
Negative: "Premium users do NOT get free shipping on oversized items"
```

### Boundary Conditions

Test values at the exact threshold:

```
Rule: "Orders over $50 qualify for free shipping"

Boundary examples:
  $49.99 → no free shipping (just below)
  $50.00 → free shipping (exact boundary)
  $50.01 → free shipping (just above)
  $0.00  → no free shipping (minimum)
```

---

## Translating Requirements to Scenarios

### From Requirement to Given/When/Then

| Requirement Part | Maps To | Question |
|-----------------|---------|----------|
| Precondition, context, state | **Given** | What must be true before? |
| User action, system event, trigger | **When** | What causes the behavior? |
| Observable outcome, side effect | **Then** | What should we observe after? |

### Common Requirement Patterns

**"If X then Y"** → Simple condition rule:
```gherkin
When X is true
Then status is "Y"
But otherwise status is "NOT_Y"
```

**"Only users with role Z can..."** → Authorization + condition:
```gherkin
Given roles context requires:
  | field | operator | value |
  | role  | ==       | Z     |
When <action trigger>
Then status is "ALLOWED"
But otherwise status is "DENIED"
```

**"X must be at least N and Y must be true"** → Compound AND:
```gherkin
When all conditions are met:
  | field | operator | value |
  | X     | >=       | N     |
  | Y     | ==       | true  |
```

**"Either X or Y qualifies"** → Compound OR:
```gherkin
When any condition is met:
  | field | operator | value |
  | X     | ==       | true  |
  | Y     | >=       | N     |
```

---

## Business Rule Categories

### Validation Rules (Data Correctness)

Ensure data meets structural requirements before processing:
- Field presence (required vs optional)
- Value ranges (age >= 0, price > 0)
- Format constraints (email format, phone pattern)
- Referential integrity (referenced entity exists)

**Typically blocking** — invalid data should not enter the system.

### Authorization Rules (Permission Checks)

Control who can perform which actions:
- Role-based access (admin, editor, viewer)
- Session validity (authenticated, not expired)
- Resource ownership (user can only edit their own)
- Tier-based access (premium features)

**Always blocking** — unauthorized actions must be rejected.

### Policy Rules (Business Decisions)

Encode business logic that governs operations:
- Pricing rules (discounts, surcharges)
- Eligibility rules (loan approval, insurance coverage)
- Workflow rules (approval chains, escalation)
- Compliance rules (regulatory requirements)

**May be blocking or informational** depending on whether they gate an operation.

### Classification Rules (Tiering, Flagging)

Categorize entities based on their current state:
- User tiers (free, premium, enterprise)
- Content flags (trending, viral, featured)
- Risk levels (low, medium, high)
- Priority scores (standard, expedited, urgent)

**Typically informational** — they classify but don't block.

### Threshold Rules (Limits, Quotas)

Enforce operational limits:
- Rate limiting (max requests per hour)
- Quota enforcement (storage, API calls)
- Capacity limits (max team members, max items)
- Financial limits (transaction ceiling, credit limit)

**Typically blocking** when the limit is exceeded.

---

## Blocking vs Informational Decision Framework

| Factor | Blocking | Informational |
|--------|----------|---------------|
| **Failure consequence** | Data corruption, security breach, business loss | Missed classification, suboptimal experience |
| **Reversibility** | Hard to reverse (payment, deletion) | Easy to reclassify later |
| **User expectation** | User expects rejection (invalid credit card) | User doesn't expect rejection |
| **Legal/compliance** | Required by regulation | Nice to have |
| **System integrity** | Prevents invalid state | Reports on valid state |

**Rule of thumb**: If unsatisfied rule means the operation SHOULD NOT proceed → blocking. If it means the result is merely categorized differently → informational.

---

## Quality Indicators for Specifications

### Good Specifications Are:

| Indicator | Meaning | Anti-Sign |
|-----------|---------|-----------|
| **Independently testable** | Each scenario can run alone | Scenario depends on another scenario's side effects |
| **Business-readable** | Product owner can read and validate | Technical jargon, SQL, API details |
| **Precise** | No ambiguity in expected behavior | "Should handle appropriately", "works correctly" |
| **Complete** | Covers happy path + error cases + boundaries | Only happy path, missing error scenarios |
| **Non-redundant** | Each scenario tests unique behavior | Two scenarios test the same thing with different data |

### Specification Smell Checklist

- [ ] Every scenario has a clear behavior being specified
- [ ] No scenario depends on another for setup
- [ ] No scenario mixes multiple behaviors
- [ ] All scenarios are written in business language
- [ ] Happy path, error cases, and boundaries are all covered
- [ ] No redundant scenarios (use Scenario Outline for variations)

---

## Common Requirement Smells

### "The system should be fast"

**Problem**: Unmeasurable. What is "fast"?
**Fix**: "Search results return within 200ms for queries under 100 characters."

### "Handle errors gracefully"

**Problem**: Unspecified behavior. What happens when there's an error?
**Fix**: "When the payment gateway times out, show the user 'Payment processing delayed, please check your order status in 5 minutes.'"

### "Support all major browsers"

**Problem**: Undefined scope. Which browsers? Which versions?
**Fix**: "Support Chrome 120+, Firefox 120+, Safari 17+, Edge 120+."

### Implicit Assumptions

**Problem**: "Users can view their orders" — what about users who aren't logged in? Users with no orders? Deleted orders?
**Fix**: Explicitly address each state: logged-out user sees login prompt, empty state shows "No orders yet", deleted orders are excluded.

### Missing Negative Cases

**Problem**: Only happy paths specified. No scenarios for what happens when things go wrong.
**Fix**: For every positive scenario, write at least one negative: "What happens if the user does NOT meet the criteria?"

### Overlapping Rules

**Problem**: Two rules could both apply to the same entity with conflicting outcomes.
**Fix**: Define rule priority or make conditions mutually exclusive. Document which rule takes precedence.
