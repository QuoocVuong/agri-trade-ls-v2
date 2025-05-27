-- V1__create_core_user_tables.sql

-- Bảng Roles
CREATE TABLE roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) UNIQUE NOT NULL COMMENT 'ROLE_ADMIN, ROLE_FARMER, ROLE_CONSUMER, ROLE_BUSINESS_BUYER',
    description VARCHAR(255) NULL
);

-- Bảng Permissions
CREATE TABLE permissions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL COMMENT 'Tên quyền hạn, e.g., USER_MANAGE',
    description VARCHAR(255) NULL
);
CREATE UNIQUE INDEX idx_permissions_name ON permissions(name);

-- Bảng Role_Permissions (Many-to-Many giữa Roles và Permissions)
CREATE TABLE role_permissions (
    role_id INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Bảng Users
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) UNIQUE,
    avatar_url VARCHAR(512),
    provider VARCHAR(20) NULL COMMENT 'Nguồn gốc tài khoản: LOCAL, GOOGLE',
    provider_id VARCHAR(255) NULL COMMENT 'ID từ nhà cung cấp OAuth2',
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    verification_token VARCHAR(100) UNIQUE NULL,
    verification_token_expiry TIMESTAMP NULL,
    refresh_token VARCHAR(512) NULL,
    refresh_token_expiry_date TIMESTAMP NULL,
    follower_count INT NOT NULL DEFAULT 0,
    following_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone_number ON users(phone_number);
CREATE INDEX idx_users_verification_token ON users(verification_token);
CREATE INDEX idx_users_is_deleted_is_active ON users(is_deleted, is_active);


-- Bảng User_Roles (Many-to-Many giữa Users và Roles)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- Bảng Addresses (Địa chỉ của User)
CREATE TABLE addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    address_detail VARCHAR(255) NOT NULL,
    province_code VARCHAR(10) NOT NULL,
    district_code VARCHAR(10) NOT NULL,
    ward_code VARCHAR(10) NOT NULL,
    type ENUM('SHIPPING', 'BILLING', 'OTHER') NOT NULL DEFAULT 'SHIPPING',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_addresses_user_id ON addresses(user_id);
CREATE INDEX idx_addresses_is_deleted ON addresses(is_deleted);

-- Bảng Farmer Profiles
CREATE TABLE farmer_profiles (
    user_id BIGINT PRIMARY KEY,
    farm_name VARCHAR(255) NOT NULL,
    description TEXT,
    address_detail VARCHAR(255),
    province_code VARCHAR(10) NOT NULL,
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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_farmer_profiles_province ON farmer_profiles(province_code);
CREATE INDEX idx_farmer_profiles_status ON farmer_profiles(verification_status);

-- Bảng Business Profiles
CREATE TABLE business_profiles (
    user_id BIGINT PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(20) UNIQUE,
    industry VARCHAR(100),
    business_phone VARCHAR(20),
    business_address_detail VARCHAR(255),
    business_province_code VARCHAR(10) NOT NULL,
    business_district_code VARCHAR(10),
    business_ward_code VARCHAR(10),
    contact_person VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_business_profiles_province ON business_profiles(business_province_code);