package com.hi.api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Đăng ký module xử lý ngày tháng (LocalDate, LocalDateTime) của Java 8+
        // Vì khởi tạo tay nên bắt buộc phải có dòng này, nếu không sẽ lỗi parse Date
        mapper.registerModule(new JavaTimeModule());

        // Tắt tính năng ném lỗi khi lưu ngày tháng dưới dạng Timestamp
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Bỏ qua lỗi nếu JSON có trường dữ liệu mà Class Java không có
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}
