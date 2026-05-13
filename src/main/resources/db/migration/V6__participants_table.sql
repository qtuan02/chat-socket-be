CREATE TABLE IF NOT EXISTS participants (
    conversation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER', -- Enum: 'ADMIN', 'MEMBER'
    last_read_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),


    CONSTRAINT fk_part_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_part_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_part_last_msg FOREIGN KEY (last_read_message_id) REFERENCES messages(id) ON DELETE SET NULL
);

CREATE INDEX idx_participants_user_id ON participants(user_id);