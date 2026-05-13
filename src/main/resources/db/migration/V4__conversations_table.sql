CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL, -- Enum: 'DIRECT', 'GROUP'
    group_name VARCHAR(255),
    created_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conv_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);