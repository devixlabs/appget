@domain:appget
Feature: Appget Domain Business Rules

  @target:employees @rule:CountryOfOriginCheck
  Scenario: Country of origin must be USA
    When country_of_origin equals "USA"
    Then status is "APPROVED"
    But otherwise status is "REJECTED"

  @target:employees @blocking @rule:EmployeeAgeCheck
  Scenario: Employee must be over 18
    When age is greater than 18
    Then status is "APPROVED"
    But otherwise status is "REJECTED"

  @target:employees @rule:EmployeeRoleCheck
  Scenario: Employee must hold Manager role
    When role_id equals "Manager"
    Then status is "APPROVED"
    But otherwise status is "REJECTED"

  @target:employees @rule:SeniorManagerCheck
  Scenario: Senior manager must be 30+ and a Manager
    When all conditions are met:
      | field   | operator | value   |
      | age     | >=       | 30      |
      | role_id | ==       | Manager |
    Then status is "SENIOR_MANAGER"
    But otherwise status is "NOT_SENIOR_MANAGER"

  @view @target:employee_salary_view @rule:HighEarnerCheck
  Scenario: High earner salary threshold
    When salary_amount is greater than 100000
    Then status is "HIGH_EARNER"
    But otherwise status is "STANDARD_EARNER"

  @target:employees @blocking @rule:AuthenticatedApproval
  Scenario: Authenticated employee approval with role level
    Given sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    And roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 3     |
    When age is at least 25
    Then status is "APPROVED_WITH_AUTH"
    But otherwise status is "DENIED"
