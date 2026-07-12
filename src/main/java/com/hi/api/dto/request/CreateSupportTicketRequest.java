package com.hi.api.dto.request;
import com.hi.api.model.SupportTicket;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class CreateSupportTicketRequest {
    @NotBlank(message="Vui lòng nhập tiêu đề") @Size(min=5,max=120,message="Tiêu đề phải có từ 5 đến 120 ký tự") private String title;
    @NotNull(message="Vui lòng chọn danh mục") private SupportTicket.Category category;
    @NotBlank(message="Vui lòng nhập nội dung") @Size(min=10,max=2000,message="Nội dung phải có từ 10 đến 2000 ký tự") private String message;
}
