-- auth domain
CREATE VIEW user_oauth_view AS
SELECT
    u.id AS user_id,
    u.username AS username,
    u.email AS email,
    u.is_verified AS is_verified,
    ot.id AS oauth_token_id,
    ot.provider_id AS provider_id,
    op.provider_name AS provider_name,
    ot.access_token AS access_token,
    ot.is_valid AS is_valid
FROM users u
LEFT JOIN oauth_tokens ot ON u.id = ot.user_id
LEFT JOIN oauth_providers op ON ot.provider_id = op.id;

CREATE VIEW api_key_stats_view AS
SELECT
    ak.id AS key_id,
    ak.user_id AS user_id,
    ak.tier AS tier,
    ak.rate_limit AS rate_limit,
    ak.is_active AS is_active,
    COUNT(DISTINCT u.id) AS user_count
FROM api_keys ak
JOIN users u ON ak.user_id = u.id
GROUP BY ak.id, ak.user_id, ak.tier, ak.rate_limit, ak.is_active;

-- social domain
CREATE VIEW post_detail_view AS
SELECT
    p.id AS post_id,
    p.author_id AS author_id,
    p.content AS post_content,
    p.like_count AS like_count,
    p.comment_count AS comment_count,
    p.is_public AS is_public,
    p.is_deleted AS is_deleted,
    u.username AS author_username,
    u.is_verified AS author_verified,
    u.follower_count AS author_follower_count
FROM posts p
JOIN users u ON p.author_id = u.id;

CREATE VIEW comment_detail_view AS
SELECT
    c.id AS comment_id,
    c.post_id AS post_id,
    c.author_id AS author_id,
    c.content AS comment_content,
    c.like_count AS like_count,
    c.is_deleted AS is_deleted,
    u.username AS author_username,
    u.is_verified AS author_verified,
    p.id AS post_id_ref,
    p.author_id AS post_author_id
FROM comments c
JOIN users u ON c.author_id = u.id
JOIN posts p ON c.post_id = p.id;

CREATE VIEW user_feed_view AS
SELECT
    u.id AS user_id,
    u.username AS username,
    p.id AS post_id,
    p.content AS post_content,
    p.author_id AS post_author_id,
    pa.username AS post_author_username,
    pa.is_verified AS post_author_verified,
    p.like_count AS post_like_count,
    p.is_public AS post_is_public,
    p.is_deleted AS post_is_deleted,
    f.is_active AS follow_is_active
FROM users u
JOIN follows f ON u.id = f.follower_id
JOIN users pa ON f.following_id = pa.id
JOIN posts p ON pa.id = p.author_id
WHERE f.is_active = true AND p.is_deleted = false;

CREATE VIEW user_stats_view AS
SELECT
    u.id AS user_id,
    u.username AS username,
    u.email AS email,
    COUNT(DISTINCT p.id) AS post_count,
    SUM(p.like_count) AS total_likes,
    u.follower_count AS follower_count,
    COUNT(DISTINCT f.id) AS following_count
FROM users u
LEFT JOIN posts p ON u.id = p.author_id
LEFT JOIN follows f ON u.id = f.follower_id
GROUP BY u.id, u.username, u.email, u.follower_count;

CREATE VIEW trending_posts_view AS
SELECT
    p.id AS post_id,
    p.author_id AS author_id,
    p.content AS post_content,
    p.like_count AS like_count,
    p.comment_count AS comment_count,
    p.is_public AS is_public,
    u.username AS author_username,
    u.is_verified AS author_verified,
    p.like_count AS viral_score
FROM posts p
JOIN users u ON p.author_id = u.id
WHERE p.is_deleted = false AND p.is_public = true;

-- admin domain
CREATE VIEW user_role_view AS
SELECT
    u.id AS user_id,
    u.username AS username,
    u.email AS email,
    ur.id AS role_assignment_id,
    r.id AS role_id,
    r.role_name AS role_name,
    r.permission_level AS permission_level
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id;

CREATE VIEW moderation_queue_view AS
SELECT
    u.id AS user_id,
    u.username AS username,
    u.email AS email,
    COUNT(DISTINCT ma.id) AS action_count,
    MAX(ma.action_type) AS last_action_type,
    MAX(ma.is_active) AS has_active_actions
FROM users u
LEFT JOIN moderation_actions ma ON u.id = ma.target_user_id
GROUP BY u.id, u.username, u.email
HAVING COUNT(DISTINCT ma.id) > 0;

CREATE VIEW company_health_view AS
SELECT
    cs.id AS company_setting_id,
    cs.company_name AS company_name,
    cs.is_public AS is_public,
    COUNT(DISTINCT u.id) AS total_user_count,
    COUNT(DISTINCT p.id) AS post_count,
    COUNT(DISTINCT c.id) AS comment_count
FROM company_settings cs
LEFT JOIN users u ON u.is_active = true
LEFT JOIN posts p ON p.is_deleted = false
LEFT JOIN comments c ON c.is_deleted = false
GROUP BY cs.id, cs.company_name, cs.is_public;
