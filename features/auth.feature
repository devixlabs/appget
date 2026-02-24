@domain:auth
Feature: Auth Domain Business Rules

  @target:User @blocking @rule:UserEmailValidation
  Scenario: User email must be valid format
    When email does not equal ""
    Then status is "VALID_EMAIL"
    But otherwise status is "INVALID_EMAIL"

  @target:User @blocking @rule:UserSuspensionCheck
  Scenario: Suspended users cannot perform actions
    When is_suspended equals false
    Then status is "ACTIVE"
    But otherwise status is "ACCOUNT_SUSPENDED"

  @target:Session @blocking @rule:SessionActiveValidation
  Scenario: Session must be active
    When is_active equals true
    Then status is "VALID_SESSION"
    But otherwise status is "INVALID_SESSION"

  @target:Session @blocking @rule:SessionTokenPresence
  Scenario: Session token must not be empty
    When token does not equal ""
    Then status is "TOKEN_PRESENT"
    But otherwise status is "NO_TOKEN"

  @target:User @blocking @rule:VerifiedUserRequirement
  Scenario: Account verification status check
    When is_verified equals true
    Then status is "VERIFIED"
    But otherwise status is "UNVERIFIED"

  @target:User @rule:UserFollowingStats
  Scenario: User following count is reasonable
    When following_count is at least 0
    Then status is "VALID_COUNT"
    But otherwise status is "INVALID_COUNT"

  @target:User @rule:UserFollowerStats
  Scenario: User follower count is reasonable
    When follower_count is at least 0
    Then status is "VALID_COUNT"
    But otherwise status is "INVALID_COUNT"

  @target:User @blocking @rule:UsernamePresence
  Scenario: Username must exist
    When username does not equal ""
    Then status is "USERNAME_PRESENT"
    But otherwise status is "NO_USERNAME"

  @target:User @rule:UserAccountStatus
  Scenario: Overall user account is in good standing
    When all conditions are met:
      | field           | operator | value |
      | is_suspended    | ==       | false |
      | is_verified     | ==       | true  |
    Then status is "GOOD_STANDING"
    But otherwise status is "ACCOUNT_RESTRICTED"

  @target:User @rule:ProfileCompleteness
  Scenario: User has completed their profile with a bio
    When bio does not equal ""
    Then status is "PROFILE_COMPLETE"
    But otherwise status is "PROFILE_INCOMPLETE"
