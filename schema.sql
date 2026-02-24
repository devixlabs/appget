-- auth domain
CREATE TABLE users (
    id VARCHAR(50) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(500) NOT NULL,
    display_name VARCHAR(200),
    bio TEXT,
    is_verified BOOLEAN NOT NULL,
    is_suspended BOOLEAN NOT NULL,
    follower_count INT NOT NULL,
    following_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE sessions (
    id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    token VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- social domain
CREATE TABLE posts (
    id VARCHAR(50) NOT NULL,
    author_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    like_count INT NOT NULL,
    comment_count INT NOT NULL,
    repost_count INT NOT NULL,
    is_public BOOLEAN NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE comments (
    id VARCHAR(50) NOT NULL,
    post_id VARCHAR(50) NOT NULL,
    author_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    like_count INT NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE likes (
    id VARCHAR(50) NOT NULL,
    post_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE follows (
    id VARCHAR(50) NOT NULL,
    follower_id VARCHAR(50) NOT NULL,
    following_id VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (follower_id) REFERENCES users(id),
    FOREIGN KEY (following_id) REFERENCES users(id)
);

CREATE TABLE reposts (
    id VARCHAR(50) NOT NULL,
    post_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- admin domain
CREATE TABLE moderation_flags (
    id VARCHAR(50) NOT NULL,
    post_id VARCHAR(50),
    comment_id VARCHAR(50),
    reason VARCHAR(255) NOT NULL,
    severity_level INT NOT NULL,
    is_resolved BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (comment_id) REFERENCES comments(id)
);
