@domain:admin
Feature: Admin Domain Business Rules

  @target:ModerationFlag @blocking @rule:SeverityLevelValidation
  Scenario: Moderation flag severity level is valid
    When severity_level is at least 1
    Then status is "VALID_SEVERITY"
    But otherwise status is "INVALID_SEVERITY"

  @target:ModerationFlag @blocking @rule:ReasonPresence
  Scenario: Moderation flag must have a reason
    When reason does not equal ""
    Then status is "REASON_PROVIDED"
    But otherwise status is "NO_REASON"

  @target:ModerationFlag @rule:FlagResolutionStatus
  Scenario: Check if moderation flag has been resolved
    When is_resolved equals true
    Then status is "RESOLVED"
    But otherwise status is "PENDING"

  @target:ModerationFlag @blocking @rule:AdminAuthorizationRequired
  Scenario: Only admins can resolve moderation flags
    Given roles context requires:
      | field     | operator | value |
      | isAdmin   | ==       | true  |
    When is_resolved equals true
    Then status is "ADMIN_APPROVED"
    But otherwise status is "UNAUTHORIZED"

  @target:ModerationFlag @blocking @rule:HighSeverityEscalation
  Scenario: High severity flags require immediate attention
    When severity_level is greater than 8
    Then status is "ESCALATED"
    But otherwise status is "STANDARD"

  @target:ModerationFlag @rule:MultipleReportCheck
  Scenario: Flag indicates potential systemic issue if severity is high and not resolved
    When all conditions are met:
      | field           | operator | value |
      | severity_level  | >=       | 7     |
      | is_resolved     | ==       | false |
    Then status is "REQUIRES_REVIEW"
    But otherwise status is "NORMAL"

  @target:ModerationFlag @blocking @rule:ContentTargetValidation
  Scenario: Flag must target either a post or comment
    When any condition is met:
      | field      | operator | value |
      | post_id    | !=       | ""    |
      | comment_id | !=       | ""    |
    Then status is "VALID_TARGET"
    But otherwise status is "NO_TARGET"
