-- social domain: User profile with follow stats
CREATE VIEW user_profile_view AS
SELECT
    u.id AS user_id,
    u.username AS username,
    u.display_name AS display_name,
    u.bio AS bio,
    u.is_verified AS is_verified,
    u.is_suspended AS is_suspended,
    u.follower_count AS follower_count,
    u.following_count AS following_count
FROM users u;

-- social domain: Post with author details
CREATE VIEW post_detail_view AS
SELECT
    p.id AS post_id,
    p.content AS post_content,
    p.like_count AS like_count,
    p.comment_count AS comment_count,
    p.repost_count AS repost_count,
    p.is_public AS is_public,
    p.is_deleted AS is_deleted,
    u.username AS author_username,
    u.is_verified AS author_verified,
    u.is_suspended AS author_suspended
FROM posts p
JOIN users u ON p.author_id = u.id;

-- social domain: Comment with author details
CREATE VIEW comment_detail_view AS
SELECT
    c.id AS comment_id,
    c.post_id AS post_id,
    c.content AS comment_content,
    c.like_count AS like_count,
    c.is_deleted AS is_deleted,
    u.username AS author_username,
    u.is_verified AS author_verified
FROM comments c
JOIN users u ON c.author_id = u.id;

-- social domain: Feed posts optimized for display
CREATE VIEW feed_post_view AS
SELECT
    p.id AS post_id,
    p.author_id AS author_id,
    p.content AS post_content,
    p.like_count AS like_count,
    p.comment_count AS comment_count,
    p.repost_count AS repost_count,
    p.is_public AS is_public,
    u.username AS author_username,
    u.is_verified AS author_verified,
    u.follower_count AS author_follower_count
FROM posts p
JOIN users u ON p.author_id = u.id
WHERE p.is_deleted = false
  AND p.is_public = true
  AND u.is_suspended = false;
