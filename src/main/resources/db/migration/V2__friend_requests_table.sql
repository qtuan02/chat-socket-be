CREATE TABLE IF NOT EXISTS friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID NOT NULL,
    to_user_id UUID NOT NULL,
    message VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fr_req_from_user FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_fr_req_to_user FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    CONSTRAINT uq_friend_request UNIQUE (from_user_id, to_user_id, status)
);

CREATE INDEX idx_friend_requests_to_user_id ON friend_requests(to_user_id);