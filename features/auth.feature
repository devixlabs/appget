@domain:auth
Feature: Auth Domain Business Rules

  @target:users @blocking @rule:UserActivationCheck
  Scenario: User account must be active
    When is_active equals true
    Then status is "ACCOUNT_ACTIVE"
    But otherwise status is "ACCOUNT_INACTIVE"

  @target:users @rule:UserVerificationStatus
  Scenario: User can be verified badge holder
    When is_verified equals true
    Then status is "VERIFIED_USER"
    But otherwise status is "UNVERIFIED_USER"

  @target:oauth_tokens @blocking @rule:OAuthTokenValidity
  Scenario: OAuth token must be valid
    When is_valid equals true
    Then status is "TOKEN_VALID"
    But otherwise status is "TOKEN_INVALID"

  @target:api_keys @blocking @rule:ApiKeyActiveStatus
  Scenario: API key must be active for use
    When is_active equals true
    Then status is "KEY_ACTIVE"
    But otherwise status is "KEY_INACTIVE"

  @target:api_keys @rule:ApiKeyTierClassification
  Scenario: API key tier determines rate limit
    When tier equals "PREMIUM"
    Then status is "PREMIUM_TIER"
    But otherwise status is "STANDARD_TIER"

  @target:sessions @blocking @rule:SessionActivityCheck
  Scenario: Session must be active
    When is_active equals true
    Then status is "SESSION_ACTIVE"
    But otherwise status is "SESSION_EXPIRED"

  @target:users @blocking @rule:AdminAuthenticationRequired
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

  @view @target:user_oauth_view @rule:OAuthIntegrationCheck
  Scenario: User has valid OAuth provider integration
    When is_valid equals true
    Then status is "OAUTH_CONNECTED"
    But otherwise status is "OAUTH_DISCONNECTED"

  @view @target:api_key_stats_view @rule:ApiKeyActiveAndConfigured
  Scenario: API key is active with configured rate limit
    When is_active equals true
    Then status is "API_KEY_READY"
    But otherwise status is "API_KEY_NOT_READY"
