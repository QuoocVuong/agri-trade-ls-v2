-- V3__Create_Permission_Tables.sql

-- Bảng Permissions
CREATE TABLE permissions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL COMMENT 'Tên quyền hạn, e.g., USER_MANAGE',
    description VARCHAR(255) NULL
);
CREATE UNIQUE INDEX idx_permissions_name ON permissions(name);

-- Bảng Role_Permissions (Many-to-Many)
CREATE TABLE role_permissions (
    role_id INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- (Optional but Recommended) Insert initial common permissions
-- Ví dụ các quyền cơ bản cho quản lý người dùng
INSERT INTO permissions (name, description) VALUES
('USER_READ_ALL', 'Quyền xem danh sách tất cả người dùng'),
('USER_READ_DETAIL', 'Quyền xem chi tiết người dùng'),
('USER_UPDATE_STATUS', 'Quyền cập nhật trạng thái active/inactive'),
('USER_UPDATE_ROLES', 'Quyền cập nhật vai trò người dùng'),
('USER_MANAGE_PROFILES', 'Quyền quản lý (duyệt/từ chối) profiles'),
-- Ví dụ quyền cho sản phẩm
('PRODUCT_MANAGE_OWN', 'Quyền quản lý sản phẩm của chính mình (Farmer)'),
('PRODUCT_MANAGE_ALL', 'Quyền quản lý tất cả sản phẩm (Admin)'),
('PRODUCT_READ', 'Quyền xem sản phẩm'),
-- Ví dụ quyền cho đơn hàng
('ORDER_CREATE', 'Quyền tạo đơn hàng (Consumer, Business Buyer)'),
('ORDER_READ_OWN', 'Quyền xem đơn hàng của mình (Buyer, Farmer)'),
('ORDER_READ_ALL', 'Quyền xem tất cả đơn hàng (Admin)'),
('ORDER_UPDATE_STATUS_OWN', 'Quyền cập nhật trạng thái đơn hàng của mình (Farmer)'),
('ORDER_UPDATE_STATUS_ALL', 'Quyền cập nhật trạng thái mọi đơn hàng (Admin)');
-- Thêm các quyền khác tùy theo chức năng của bạn

-- (Optional but Recommended) Assign initial permissions to roles
-- Ví dụ gán quyền cho ADMIN
SET @admin_role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @admin_role_id, id FROM permissions WHERE name LIKE 'USER_%' OR name LIKE 'PRODUCT_MANAGE_ALL' OR name LIKE 'ORDER_%ALL'; -- Gán hết quyền user, product all, order all

-- Ví dụ gán quyền cho FARMER
SET @farmer_role_id = (SELECT id FROM roles WHERE name = 'ROLE_FARMER');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @farmer_role_id, id FROM permissions WHERE name IN ('PRODUCT_MANAGE_OWN', 'ORDER_READ_OWN', 'ORDER_UPDATE_STATUS_OWN', 'PRODUCT_READ');

-- Ví dụ gán quyền cho CONSUMER
SET @consumer_role_id = (SELECT id FROM roles WHERE name = 'ROLE_CONSUMER');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @consumer_role_id, id FROM permissions WHERE name IN ('ORDER_CREATE', 'ORDER_READ_OWN', 'PRODUCT_READ');

-- Ví dụ gán quyền cho BUSINESS_BUYER
SET @business_role_id = (SELECT id FROM roles WHERE name = 'ROLE_BUSINESS_BUYER');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @business_role_id, id FROM permissions WHERE name IN ('ORDER_CREATE', 'ORDER_READ_OWN', 'PRODUCT_READ'); -- Giống Consumer ban đầu, có thể thêm quyền báo giá sau