-- V4__Add_User_Verification_Tokens.sql

ALTER TABLE users
ADD COLUMN verification_token VARCHAR(100) NULL UNIQUE AFTER is_active,
ADD COLUMN verification_token_expiry TIMESTAMP NULL AFTER verification_token;

-- Thêm index cho token để tìm kiếm nhanh
CREATE INDEX idx_users_verification_token ON users(verification_token);

-- Cập nhật trạng thái active mặc định thành FALSE cho user mới
-- Lưu ý: Câu lệnh này chỉ ảnh hưởng user tạo SAU KHI chạy migration này
-- Nếu muốn đổi user cũ, cần chạy lệnh UPDATE riêng
ALTER TABLE users MODIFY COLUMN is_active BOOLEAN NOT NULL DEFAULT FALSE;