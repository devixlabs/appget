@domain:social
Feature: Social Domain Business Rules

  @target:Post @blocking @rule:PublicPostVerification
  Scenario: Public posts require verified author
    When is_public equals true
    Then status is "ALLOWED"
    But otherwise status is "AUTHOR_NOT_VERIFIED"

  @target:Post @blocking @rule:PostContentValidation
  Scenario: Post content must not be empty
    When content does not equal ""
    Then status is "VALID"
    But otherwise status is "EMPTY_CONTENT"

  @target:Comment @blocking @rule:CommentCreationValidation
  Scenario: Comments can only be posted on active, non-deleted posts
    When is_deleted equals false
    Then status is "ALLOWED"
    But otherwise status is "POST_NOT_AVAILABLE"

  @target:Follow @blocking @rule:ActiveFollowValidation
  Scenario: Follow relationship must be active
    When is_active equals true
    Then status is "VALID"
    But otherwise status is "FOLLOW_INACTIVE"

  @target:Follow @rule:ActiveFollowCheck
  Scenario: Follow relationship is active
    When is_active equals true
    Then status is "ACTIVE"
    But otherwise status is "INACTIVE"

  @view @target:PostDetailView @rule:HighEngagementPost
  Scenario: Post has significant engagement
    When all conditions are met:
      | field      | operator | value |
      | like_count | >=       | 1000  |
      | is_public  | ==       | true  |
    Then status is "HIGH_ENGAGEMENT"
    But otherwise status is "LOW_ENGAGEMENT"

  @view @target:PostDetailView @rule:VerifiedAuthorPriority
  Scenario: Post by verified author
    When author_verified equals true
    Then status is "PRIORITY"
    But otherwise status is "STANDARD"

  @view @target:FeedPostView @rule:FeedPostEligibility
  Scenario: Post is eligible for user feed
    When is_public equals true
    Then status is "ELIGIBLE"
    But otherwise status is "FILTERED"

  @target:Post @rule:PostDeletionCheck
  Scenario: Deleted posts are marked for archival
    When is_deleted equals true
    Then status is "ARCHIVED"
    But otherwise status is "ACTIVE"

  @view @target:CommentDetailView @rule:AuthorVerificationInComments
  Scenario: Comment from verified author gets priority
    When author_verified equals true
    Then status is "VERIFIED_AUTHOR"
    But otherwise status is "UNVERIFIED_AUTHOR"
