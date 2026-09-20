-- Existing rows were written as JVM wall-clock; dev data, treated as UTC (see spec §1).
ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE friend_requests
    ALTER COLUMN responded_at TYPE TIMESTAMPTZ USING responded_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE friends
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE conversations
    ALTER COLUMN last_message_at TYPE TIMESTAMPTZ USING last_message_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE messages
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE participants
    ALTER COLUMN last_read_at TYPE TIMESTAMPTZ USING last_read_at AT TIME ZONE 'UTC',
    ALTER COLUMN joined_at TYPE TIMESTAMPTZ USING joined_at AT TIME ZONE 'UTC',
    ALTER COLUMN left_at TYPE TIMESTAMPTZ USING left_at AT TIME ZONE 'UTC',
    ALTER COLUMN archived_at TYPE TIMESTAMPTZ USING archived_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN muted_until TYPE TIMESTAMPTZ USING muted_until AT TIME ZONE 'UTC';
