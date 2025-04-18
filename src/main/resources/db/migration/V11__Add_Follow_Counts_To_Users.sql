-- V11__Add_Follow_Counts_To_Users.sql
ALTER TABLE users ADD COLUMN follower_count INT NOT NULL DEFAULT 0 AFTER is_active; -- Hoặc vị trí phù hợp
ALTER TABLE users ADD COLUMN following_count INT NOT NULL DEFAULT 0 AFTER follower_count;