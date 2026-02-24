@domain:admin
Feature: Admin Domain Business Rules

  @target:Roles @rule:AdminRoleClassification
  Scenario: Role is classified by permission level
    When permission_level is at least 2
    Then status is "ELEVATED_ROLE"
    But otherwise status is "BASIC_ROLE"

  @target:UserRoles @blocking @rule:UserRoleAssignmentValid
  Scenario: User role assignment is authorized
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 4     |
    When role_id does not equal ""
    Then status is "ROLE_ASSIGNED"
    But otherwise status is "ROLE_NOT_ASSIGNED"

  @target:ModerationActions @blocking @rule:ModerationActionActive
  Scenario: Moderation action must be active to enforce
    When is_active equals true
    Then status is "ACTION_ENFORCED"
    But otherwise status is "ACTION_INACTIVE"

  @target:ModerationActions @blocking @rule:ModerationAuthorizationCheck
  Scenario: Only admins can perform moderation
    Given roles context requires:
      | field     | operator | value |
      | isAdmin   | ==       | true  |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_active equals true
    Then status is "MODERATION_AUTHORIZED"
    But otherwise status is "MODERATION_DENIED"

  @target:CompanySettings @rule:CompanyPublicStatus
  Scenario: Company can be public or private
    When is_public equals true
    Then status is "PUBLIC_COMPANY"
    But otherwise status is "PRIVATE_COMPANY"

  @view @target:UserRoleView @blocking @rule:UserHasAdminRole
  Scenario: User with high permission level is admin
    When permission_level is at least 4
    Then status is "ADMIN_USER"
    But otherwise status is "REGULAR_USER"

  @view @target:ModerationQueueView @rule:UserNeedsModerationReview
  Scenario: User has active moderation actions
    When action_count is greater than 0
    Then status is "NEEDS_REVIEW"
    But otherwise status is "NO_ACTION_NEEDED"

  @view @target:ModerationQueueView @rule:PriorityModerationFlag
  Scenario: Multiple moderation actions flag user for priority review
    When action_count is at least 3
    Then status is "PRIORITY_REVIEW"
    But otherwise status is "STANDARD_REVIEW"

  @view @target:CompanyHealthView @rule:CompanyGrowthMetric
  Scenario: Company has healthy user growth
    When total_user_count is at least 5
    Then status is "HEALTHY_GROWTH"
    But otherwise status is "BOOTSTRAP_STAGE"

  @view @target:CompanyHealthView @rule:PlatformEngagementMetric
  Scenario: Platform has strong engagement
    When all conditions are met:
      | field           | operator | value |
      | post_count      | >=       | 2     |
      | comment_count   | >=       | 1     |
    Then status is "STRONG_ENGAGEMENT"
    But otherwise status is "EARLY_STAGE"
