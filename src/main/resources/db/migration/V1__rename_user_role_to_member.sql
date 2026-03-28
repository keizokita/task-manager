-- Migration: Rename UserRole.USER to MEMBER
-- Run this manually against the database after deploying the code change
UPDATE users SET role = 'MEMBER' WHERE role = 'USER';
