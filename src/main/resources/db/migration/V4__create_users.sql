-- V4: Create users table for authentication
CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       username VARCHAR(40) NOT NULL UNIQUE,
                       password_hash VARCHAR(100) NOT NULL,
                       created_at TIMESTAMP DEFAULT now(),
                       updated_at TIMESTAMP DEFAULT now()
);

-- Updated_at trigger
CREATE OR REPLACE FUNCTION set_users_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_users_updated_at();

-- Insert default bootstrap user for migrating existing data (password: 'password')
INSERT INTO users (id, username, password_hash) VALUES (
                                                           '00000000-0000-0000-0000-000000000000',
                                                           'default',
                                                           '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5s8dOa5Hf36fY4LEGy6Y6q5H0wE4.'
                                                       );