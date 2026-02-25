@domain:social
Feature: Social Domain Business Rules

  @target:Posts @rule:PostPublicityStatus
  Scenario: Post visibility depends on public flag
    When is_public equals true
    Then status is "PUBLIC_POST"
    But otherwise status is "PRIVATE_POST"

  @target:Posts @blocking @rule:PostNotDeletedCheck
  Scenario: Post must not be deleted to be viewable
    When is_deleted equals false
    Then status is "POST_VIEWABLE"
    But otherwise status is "POST_DELETED"

  @target:Posts @rule:ViralPostDetection
  Scenario: Post is viral with high engagement
    When all conditions are met:
      | field      | operator | value |
      | like_count | >=       | 1000  |
      | is_deleted | ==       | false |
    Then status is "VIRAL"
    But otherwise status is "STANDARD"

  @target:Comments @blocking @rule:CommentNotDeletedCheck
  Scenario: Comment must not be deleted to exist
    When is_deleted equals false
    Then status is "COMMENT_ACTIVE"
    But otherwise status is "COMMENT_DELETED"

  @target:Comments @rule:PopularCommentDetection
  Scenario: Comment is popular with engagement
    When like_count is at least 100
    Then status is "POPULAR_COMMENT"
    But otherwise status is "NORMAL_COMMENT"

  @target:Likes @blocking @rule:LikeActiveStatus
  Scenario: Like must be active to count
    When is_active equals true
    Then status is "LIKE_ACTIVE"
    But otherwise status is "LIKE_REMOVED"

  @target:Follows @blocking @rule:FollowActiveStatus
  Scenario: Follow relationship must be active
    When is_active equals true
    Then status is "FOLLOWING"
    But otherwise status is "NOT_FOLLOWING"

  @target:Feeds @rule:FeedFollowingStatus
  Scenario: User feed follows at least one account
    When is_following_feed equals true
    Then status is "FEED_ACTIVE"
    But otherwise status is "FEED_EMPTY"

  @view @target:PostDetailView @blocking @rule:PublicPostViewable
  Scenario: Post detail is viewable if public and not deleted
    When all conditions are met:
      | field      | operator | value |
      | is_public  | ==       | true  |
      | is_deleted | ==       | false |
    Then status is "POST_ACCESSIBLE"
    But otherwise status is "POST_HIDDEN"

  @view @target:PostDetailView @rule:VerifiedAuthorPriority
  Scenario: Post by verified author gets priority display
    When author_verified equals true
    Then status is "PRIORITY_DISPLAY"
    But otherwise status is "NORMAL_DISPLAY"

  @view @target:CommentDetailView @rule:HighEngagementComment
  Scenario: Comment with high engagement is featured
    When like_count is greater than 500
    Then status is "FEATURED_COMMENT"
    But otherwise status is "REGULAR_COMMENT"

  @view @target:UserStatsView @rule:ActiveContentCreator
  Scenario: User is active content creator
    When post_count is at least 10
    Then status is "ACTIVE_CREATOR"
    But otherwise status is "CASUAL_USER"

  @view @target:TrendingPostsView @rule:TrendingPostScoring
  Scenario: Post achieves trending status with viral score
    When viral_score is greater than 5000
    Then status is "TRENDING"
    But otherwise status is "NOT_TRENDING"

  @view @target:UserFeedView @blocking @rule:FeedPostAccessible
  Scenario: Feed post must be from active follow and not deleted
    When all conditions are met:
      | field             | operator | value |
      | follow_is_active  | ==       | true  |
      | post_is_deleted   | ==       | false |
    Then status is "FEED_POST_ELIGIBLE"
    But otherwise status is "FEED_POST_FILTERED"
