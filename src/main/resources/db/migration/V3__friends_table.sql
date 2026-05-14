CREATE TABLE IF NOT EXISTS friends (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id UUID NOT NULL,
    user_b_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_friends_user_a FOREIGN KEY (user_a_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friends_user_b FOREIGN KEY (user_b_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_distinct CHECK (user_a_id <> user_b_id),
    CONSTRAINT chk_user_order CHECK (user_a_id < user_b_id),
    CONSTRAINT uq_friends UNIQUE (user_a_id, user_b_id)
);

CREATE INDEX idx_friends_user_a_id ON friends(user_a_id);
CREATE INDEX idx_friends_user_b_id ON friends(user_b_id);
