-- V6__insert_initial_roles_and_permissions.sql

-- Insert initial roles
INSERT IGNORE INTO roles (name, description) VALUES
('ROLE_ADMIN', 'Quản trị viên hệ thống'),
('ROLE_FARMER', 'Người bán / Nông dân'),
('ROLE_CONSUMER', 'Người mua lẻ'),
('ROLE_BUSINESS_BUYER', 'Người mua doanh nghiệp');

-- Insert initial common permissions
INSERT IGNORE INTO permissions (name, description) VALUES
('USER_READ_ALL', 'Quyền xem danh sách tất cả người dùng'),
('USER_READ_DETAIL', 'Quyền xem chi tiết người dùng'),
('USER_UPDATE_STATUS', 'Quyền cập nhật trạng thái active/inactive của người dùng'),
('USER_UPDATE_ROLES', 'Quyền cập nhật vai trò người dùng'),
('USER_MANAGE_PROFILES', 'Quyền quản lý (duyệt/từ chối) hồ sơ Farmer/Business'),
('PRODUCT_CREATE_OWN', 'Quyền tạo sản phẩm của chính mình (Farmer)'),
('PRODUCT_UPDATE_OWN', 'Quyền cập nhật sản phẩm của chính mình (Farmer)'),
('PRODUCT_DELETE_OWN', 'Quyền xóa sản phẩm của chính mình (Farmer)'),
('PRODUCT_MANAGE_ALL', 'Quyền quản lý tất cả sản phẩm (Admin)'),
('PRODUCT_APPROVE', 'Quyền duyệt/từ chối sản phẩm (Admin)'),
('PRODUCT_READ_PUBLIC', 'Quyền xem sản phẩm công khai'),
('CATEGORY_MANAGE', 'Quyền quản lý danh mục (Admin)'),
('ORDER_CREATE', 'Quyền tạo đơn hàng (Consumer, Business Buyer)'),
('ORDER_READ_OWN', 'Quyền xem đơn hàng của mình (Buyer, Farmer)'),
('ORDER_READ_ALL', 'Quyền xem tất cả đơn hàng (Admin)'),
('ORDER_UPDATE_STATUS_OWN', 'Quyền cập nhật trạng thái đơn hàng của mình (Farmer)'),
('ORDER_UPDATE_STATUS_ALL', 'Quyền cập nhật trạng thái mọi đơn hàng (Admin)'),
('ORDER_CANCEL_OWN', 'Quyền hủy đơn hàng của mình (Buyer, trong điều kiện cho phép)'),
('ORDER_CANCEL_ANY', 'Quyền hủy bất kỳ đơn hàng nào (Admin)'),
('REVIEW_CREATE', 'Quyền tạo đánh giá'),
('REVIEW_MANAGE', 'Quyền quản lý (duyệt/từ chối/xóa) đánh giá (Admin)'),
('PERMISSION_MANAGE', 'Quyền quản lý các quyền hạn (Admin)'),
('ROLE_MANAGE', 'Quyền quản lý các vai trò (Admin)'),
('CHAT_ACCESS', 'Quyền truy cập tính năng chat'),
('FOLLOW_USER', 'Quyền theo dõi người dùng khác'),
('FAVORITE_PRODUCT', 'Quyền yêu thích sản phẩm'),
('VIEW_ADMIN_DASHBOARD', 'Quyền xem dashboard của Admin'),
('VIEW_FARMER_DASHBOARD', 'Quyền xem dashboard của Farmer'),
('MANAGE_FILES', 'Quyền quản lý file (upload/delete - có thể cần chi tiết hơn)');

-- Assign initial permissions to roles (Ví dụ cơ bản)
-- ROLE_ADMIN
SET @admin_role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @admin_role_id, p.id FROM permissions p
WHERE p.name IN (
    'USER_READ_ALL', 'USER_READ_DETAIL', 'USER_UPDATE_STATUS', 'USER_UPDATE_ROLES', 'USER_MANAGE_PROFILES',
    'PRODUCT_MANAGE_ALL', 'PRODUCT_APPROVE', 'PRODUCT_READ_PUBLIC',
    'CATEGORY_MANAGE',
    'ORDER_READ_ALL', 'ORDER_UPDATE_STATUS_ALL', 'ORDER_CANCEL_ANY',
    'REVIEW_MANAGE',
    'PERMISSION_MANAGE', 'ROLE_MANAGE',
    'VIEW_ADMIN_DASHBOARD', 'MANAGE_FILES', 'CHAT_ACCESS'
);

-- ROLE_FARMER
SET @farmer_role_id = (SELECT id FROM roles WHERE name = 'ROLE_FARMER');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @farmer_role_id, p.id FROM permissions p
WHERE p.name IN (
    'PRODUCT_CREATE_OWN', 'PRODUCT_UPDATE_OWN', 'PRODUCT_DELETE_OWN', 'PRODUCT_READ_PUBLIC',
    'ORDER_READ_OWN', 'ORDER_UPDATE_STATUS_OWN',
    'VIEW_FARMER_DASHBOARD', 'CHAT_ACCESS', 'MANAGE_FILES' -- Cho phép farmer upload ảnh sản phẩm
);

-- ROLE_CONSUMER
SET @consumer_role_id = (SELECT id FROM roles WHERE name = 'ROLE_CONSUMER');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @consumer_role_id, p.id FROM permissions p
WHERE p.name IN (
    'PRODUCT_READ_PUBLIC', 'ORDER_CREATE', 'ORDER_READ_OWN', 'ORDER_CANCEL_OWN',
    'REVIEW_CREATE', 'CHAT_ACCESS', 'FOLLOW_USER', 'FAVORITE_PRODUCT', 'MANAGE_FILES' -- Cho phép user upload avatar
);

-- ROLE_BUSINESS_BUYER (Có thể giống Consumer hoặc có thêm quyền riêng)
SET @business_buyer_role_id = (SELECT id FROM roles WHERE name = 'ROLE_BUSINESS_BUYER');
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT @business_buyer_role_id, p.id FROM permissions p
WHERE p.name IN (
    'PRODUCT_READ_PUBLIC', 'ORDER_CREATE', 'ORDER_READ_OWN', 'ORDER_CANCEL_OWN',
    'REVIEW_CREATE', 'CHAT_ACCESS', 'FOLLOW_USER', 'FAVORITE_PRODUCT', 'MANAGE_FILES'
    -- Có thể thêm các quyền đặc thù cho B2B sau này, ví dụ: REQUEST_QUOTE
);