@domain:auth
Feature: Auth Domain Business Rules

  @target:Users @blocking @rule:UserActivationCheck
  Scenario: User account must be active
    When is_active equals true
    Then status is "ACCOUNT_ACTIVE"
    But otherwise status is "ACCOUNT_INACTIVE"

  @target:Users @rule:UserVerificationStatus
  Scenario: User can be verified badge holder
    When is_verified equals true
    Then status is "VERIFIED_USER"
    But otherwise status is "UNVERIFIED_USER"

  @target:OauthTokens @blocking @rule:OAuthTokenValidity
  Scenario: OAuth token must be valid
    When is_valid equals true
    Then status is "TOKEN_VALID"
    But otherwise status is "TOKEN_INVALID"

  @target:ApiKeys @blocking @rule:ApiKeyActiveStatus
  Scenario: API key must be active for use
    When is_active equals true
    Then status is "KEY_ACTIVE"
    But otherwise status is "KEY_INACTIVE"

  @target:ApiKeys @rule:ApiKeyTierClassification
  Scenario: API key tier determines rate limit
    When tier equals "PREMIUM"
    Then status is "PREMIUM_TIER"
    But otherwise status is "STANDARD_TIER"

  @target:Sessions @blocking @rule:SessionActivityCheck
  Scenario: Session must be active
    When is_active equals true
    Then status is "SESSION_ACTIVE"
    But otherwise status is "SESSION_EXPIRED"

  @target:Users @blocking @rule:AdminAuthenticationRequired
  Scenario: User with admin role can manage system
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 3     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_active equals true
    Then status is "ADMIN_AUTHENTICATED"
    But otherwise status is "ADMIN_DENIED"

  @view @target:UserOauthView @rule:OAuthIntegrationCheck
  Scenario: User has valid OAuth provider integration
    When is_valid equals true
    Then status is "OAUTH_CONNECTED"
    But otherwise status is "OAUTH_DISCONNECTED"

  @view @target:ApiKeyStatsView @rule:ApiKeyActiveAndConfigured
  Scenario: API key is active with configured rate limit
    When is_active equals true
    Then status is "API_KEY_READY"
    But otherwise status is "API_KEY_NOT_READY"
