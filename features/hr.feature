@domain:hr
Feature: HR Domain Business Rules

  @target:salaries @rule:SalaryAmountCheck
  Scenario: Salary premium threshold
    When amount is greater than 50000
    Then status is "PREMIUM"
    But otherwise status is "STANDARD"
