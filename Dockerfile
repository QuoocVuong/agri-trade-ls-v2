# Sử dụng một base image OpenJDK 21 chính thức
FROM openjdk:21-jdk-slim

# Đặt thư mục làm việc bên trong container
WORKDIR /app

# Sao chép file .jar đã được build từ thư mục target của Maven vào container
# Tên file .jar có thể khác, hãy kiểm tra trong thư mục target/ của bạn sau khi build
COPY target/agri-trade-ls-0.0.1-SNAPSHOT.jar app.jar

# Expose cổng mà ứng dụng Spring Boot sẽ chạy bên trong container
EXPOSE 8080

# Lệnh để chạy ứng dụng khi container khởi động
# Các biến môi trường sẽ được truyền vào từ file docker-compose.yml
ENTRYPOINT ["java", "-jar", "app.jar"]