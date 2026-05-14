CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL, -- Enum: 'DIRECT', 'GROUP'
    group_name VARCHAR(255),
    created_by UUID,
    direct_user_a_id UUID,
    direct_user_b_id UUID,
    last_message_id UUID,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conv_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_conv_direct_user_a FOREIGN KEY (direct_user_a_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_direct_user_b FOREIGN KEY (direct_user_b_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_conversation_users_for_type
        CHECK (
            (type = 'DIRECT' AND direct_user_a_id IS NOT NULL AND direct_user_b_id IS NOT NULL AND direct_user_a_id < direct_user_b_id)
            OR
            (type = 'GROUP' AND direct_user_a_id IS NULL AND direct_user_b_id IS NULL)
        ),
    CONSTRAINT chk_conversation_direct_users_distinct
        CHECK (direct_user_a_id <> direct_user_b_id),
    CONSTRAINT chk_conversation_type CHECK (type IN ('DIRECT', 'GROUP'))
);

CREATE UNIQUE INDEX uq_direct_conversations_pair
    ON conversations(direct_user_a_id, direct_user_b_id)
    WHERE type = 'DIRECT';

CREATE INDEX idx_conversations_last_message_at ON conversations(last_message_at DESC);
