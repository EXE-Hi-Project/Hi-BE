package com.hi.api.controller;

import com.hi.api.model.User;
import com.hi.api.service.CoupleQuestionService;
import com.hi.api.service.PartnerCareSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PartnerExperienceControllerTest {

    @Test
    void skipEndpointIsGone() {
        PartnerExperienceController controller = new PartnerExperienceController(
                mock(CoupleQuestionService.class),
                mock(PartnerCareSuggestionService.class)
        );
        User user = new User();
        user.setId("user-a");

        ResponseEntity<Map<String, Object>> response = controller.skip(user);

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals(false, response.getBody().get("success"));
    }
}
