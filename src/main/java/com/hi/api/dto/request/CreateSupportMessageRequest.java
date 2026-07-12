package com.hi.api.dto.request;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class CreateSupportMessageRequest {
    @NotBlank(message="Vui lòng nhập nội dung") @Size(min=10,max=2000,message="Nội dung phải có từ 10 đến 2000 ký tự") private String message;
}
