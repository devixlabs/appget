-- auth domain
CREATE TABLE users (
    id VARCHAR(50) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(200),
    bio TEXT,
    is_verified BOOLEAN NOT NULL,
    follower_count INT NOT NULL,
    is_active BOOLEAN NOT NULL
);

CREATE TABLE oauth_providers (
    id VARCHAR(50) NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
    scope VARCHAR(500) NOT NULL
);

CREATE TABLE oauth_tokens (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    provider_id VARCHAR(50) NOT NULL,
    access_token VARCHAR(1000) NOT NULL,
    refresh_token VARCHAR(1000),
    is_valid BOOLEAN NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (provider_id) REFERENCES oauth_providers(id)
);

CREATE TABLE api_keys (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    key_hash VARCHAR(500) NOT NULL,
    tier VARCHAR(50) NOT NULL,
    rate_limit INT NOT NULL,
    is_active BOOLEAN NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE sessions (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    token VARCHAR(1000) NOT NULL,
    is_active BOOLEAN NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- social domain
CREATE TABLE posts (
    id VARCHAR(50) NOT NULL,
    author_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    like_count INT NOT NULL,
    comment_count INT NOT NULL,
    is_public BOOLEAN NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE comments (
    id VARCHAR(50) NOT NULL,
    post_id VARCHAR(50) NOT NULL,
    author_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    like_count INT NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE likes (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    likeable_type VARCHAR(50) NOT NULL,
    likeable_id VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE follows (
    id VARCHAR(50) NOT NULL,
    follower_id VARCHAR(50) NOT NULL,
    following_id VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL,
    FOREIGN KEY (follower_id) REFERENCES users(id),
    FOREIGN KEY (following_id) REFERENCES users(id)
);

CREATE TABLE feeds (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    is_following_feed BOOLEAN NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- admin domain
CREATE TABLE roles (
    id VARCHAR(50) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    permission_level INT NOT NULL
);

CREATE TABLE user_roles (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    role_id VARCHAR(50) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE moderation_actions (
    id VARCHAR(50) NOT NULL,
    moderator_id VARCHAR(50) NOT NULL,
    target_user_id VARCHAR(50) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL,
    FOREIGN KEY (moderator_id) REFERENCES users(id),
    FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE TABLE company_settings (
    id VARCHAR(50) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    is_public BOOLEAN NOT NULL,
    feature_flags VARCHAR(500) NOT NULL
);
