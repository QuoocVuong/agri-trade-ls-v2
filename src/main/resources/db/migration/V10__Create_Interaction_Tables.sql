-- V10__Create_Interaction_Tables.sql

-- Bảng Chat Rooms (Phòng chat giữa 2 user)
CREATE TABLE chat_rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id_1 BIGINT NOT NULL COMMENT 'ID user 1 (luôn nhỏ hơn user_id_2)',
    user_id_2 BIGINT NOT NULL COMMENT 'ID user 2 (luôn lớn hơn user_id_1)',
    last_message_id BIGINT NULL COMMENT 'FK chat_messages.id (sẽ tạo FK sau)',
    last_message_time TIMESTAMP NULL,
    user1_unread_count INT NOT NULL DEFAULT 0,
    user2_unread_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_users_chat (user_id_1, user_id_2),
    FOREIGN KEY (user_id_1) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id_2) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_chat_rooms_user1 ON chat_rooms(user_id_1);
CREATE INDEX idx_chat_rooms_user2 ON chat_rooms(user_id_2);
CREATE INDEX idx_chat_rooms_last_message_time ON chat_rooms(last_message_time);

-- Bảng Chat Messages (Tin nhắn)
CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL COMMENT 'FK users.id',
    recipient_id BIGINT NOT NULL COMMENT 'FK users.id',
    content TEXT NOT NULL,
    message_type ENUM('TEXT', 'IMAGE', 'FILE', 'SYSTEM') NOT NULL DEFAULT 'TEXT',
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_chat_messages_room_id_sent_at ON chat_messages(room_id, sent_at);
CREATE INDEX idx_chat_messages_recipient_read ON chat_messages(recipient_id, is_read);

-- Thêm FK cho last_message_id trong chat_rooms sau khi chat_messages được tạo
ALTER TABLE chat_rooms
ADD CONSTRAINT fk_chat_rooms_last_message
FOREIGN KEY (last_message_id) REFERENCES chat_messages(id) ON DELETE SET NULL;


-- Bảng User Follows (Theo dõi user - thường là buyer theo dõi farmer)
CREATE TABLE user_follows (
    follower_id BIGINT NOT NULL COMMENT 'Người đi theo dõi',
    following_id BIGINT NOT NULL COMMENT 'Người được theo dõi',
    followed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_follows_follower ON user_follows(follower_id);
CREATE INDEX idx_user_follows_following ON user_follows(following_id);

-- Bảng Favorite Products (Sản phẩm yêu thích)
CREATE TABLE favorite_products (
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_favorite_products_user ON favorite_products(user_id);
CREATE INDEX idx_favorite_products_product ON favorite_products(product_id);

-- Bảng Reviews (Đánh giá sản phẩm)
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    consumer_id BIGINT NOT NULL COMMENT 'Người đánh giá (FK users.id)',
    order_id BIGINT NULL COMMENT 'FK orders.id (Liên kết đơn hàng đã mua)',
    rating INT NOT NULL COMMENT 'Điểm đánh giá (1-5)',
    comment TEXT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'Trạng thái kiểm duyệt',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (consumer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL
);
CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_consumer_id ON reviews(consumer_id);
CREATE INDEX idx_reviews_status ON reviews(status);

-- Cập nhật bảng users và products để thêm các cột count (nếu muốn)
-- ALTER TABLE users ADD COLUMN follower_count INT NOT NULL DEFAULT 0 AFTER is_active;
-- ALTER TABLE users ADD COLUMN following_count INT NOT NULL DEFAULT 0 AFTER follower_count;
-- ALTER TABLE products ADD COLUMN favorite_count INT NOT NULL DEFAULT 0 AFTER rating_count;