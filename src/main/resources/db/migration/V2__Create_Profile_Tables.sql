-- V2__Create_Profile_Tables.sql

-- Bảng Business Profiles (Cho người mua Doanh nghiệp)
CREATE TABLE business_profiles (
    user_id BIGINT PRIMARY KEY COMMENT 'FK to users.id',
    business_name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(20) UNIQUE,
    industry VARCHAR(100),
    business_phone VARCHAR(20),
    business_address_detail VARCHAR(255),
    business_province_code VARCHAR(10) NOT NULL, -- Quan trọng cho lọc B2B
    business_district_code VARCHAR(10),
    business_ward_code VARCHAR(10),
    contact_person VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_business_profiles_province ON business_profiles(business_province_code);

-- Bảng Farmer Profiles (Cho Nông dân/Người bán)
CREATE TABLE farmer_profiles (
    user_id BIGINT PRIMARY KEY COMMENT 'FK to users.id',
    farm_name VARCHAR(255) NOT NULL,
    description TEXT,
    address_detail VARCHAR(255),
    province_code VARCHAR(10) NOT NULL, -- Quan trọng cho lọc B2B
    district_code VARCHAR(10),
    ward_code VARCHAR(10),
    cover_image_url VARCHAR(512),
    verification_status ENUM('PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMP NULL,
    verified_by BIGINT NULL COMMENT 'FK to users.id (Admin)',
    can_supply_b2b BOOLEAN NOT NULL DEFAULT FALSE,
    b2b_certifications LONGTEXT,
    min_b2b_order_value DECIMAL(15, 2) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE -- Để null nếu admin bị xóa
);
CREATE INDEX idx_farmer_profiles_province ON farmer_profiles(province_code);
CREATE INDEX idx_farmer_profiles_status ON farmer_profiles(verification_status);

-- (Optional) Cập nhật bảng users nếu muốn thêm các cột count (có thể tính toán khi cần)
-- ALTER TABLE users ADD COLUMN follower_count INT NOT NULL DEFAULT 0;
-- ALTER TABLE users ADD COLUMN following_count INT NOT NULL DEFAULT 0;