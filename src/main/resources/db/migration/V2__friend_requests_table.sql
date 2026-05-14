CREATE TABLE IF NOT EXISTS friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID NOT NULL,
    to_user_id UUID NOT NULL,
    message VARCHAR(300),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    responded_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fr_req_from_user FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_fr_req_to_user FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_friend_request_users_distinct CHECK (from_user_id <> to_user_id),
    CONSTRAINT chk_friend_request_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT uq_friend_request UNIQUE (from_user_id, to_user_id, status)
);

CREATE INDEX idx_friend_requests_from_user_id ON friend_requests(from_user_id);
CREATE INDEX idx_friend_requests_to_user_id ON friend_requests(to_user_id);
CREATE INDEX idx_friend_requests_to_status_created_at ON friend_requests(to_user_id, status, created_at DESC);
CREATE INDEX idx_friend_requests_from_status_created_at ON friend_requests(from_user_id, status, created_at DESC);
CREATE UNIQUE INDEX uq_friend_requests_pending_pair ON friend_requests (
    LEAST(from_user_id, to_user_id), GREATEST(from_user_id, to_user_id)) WHERE status = 'PENDING';