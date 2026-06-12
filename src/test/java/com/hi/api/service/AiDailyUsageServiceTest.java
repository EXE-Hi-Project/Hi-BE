package com.hi.api.service;

import com.hi.api.exception.AiQuotaExceededException;
import com.hi.api.model.AiDailyUsage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiDailyUsageServiceTest {

    @ParameterizedTest
    @ValueSource(ints = {5, 50})
    void blocksTheNextAnswerAfterDailyLimit(int limit) {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AiDailyUsage stored = new AiDailyUsage();
        stored.setUserId("user-1");
        stored.setUsed(limit);

        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(AiDailyUsage.class)
        )).thenThrow(new DuplicateKeyException("daily quota reached"));
        when(mongoTemplate.findOne(any(Query.class), eq(AiDailyUsage.class))).thenReturn(stored);

        AiDailyUsageService service = new AiDailyUsageService(mongoTemplate);

        AiQuotaExceededException exception = catchThrowableOfType(
                () -> service.reserve("user-1", limit),
                AiQuotaExceededException.class
        );

        assertThat(exception.getLimit()).isEqualTo(limit);
        assertThat(exception.getUsed()).isEqualTo(limit);
    }
}
