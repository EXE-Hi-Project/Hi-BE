package com.hi.api.dto.request;
import com.hi.api.model.SupportTicket;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class UpdateSupportStatusRequest {
    @NotNull(message="Vui lòng chọn trạng thái") private SupportTicket.Status status;
}
