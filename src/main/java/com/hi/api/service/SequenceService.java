package com.hi.api.service;

import com.hi.api.model.SequenceCounter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class SequenceService {

    private final MongoTemplate mongoTemplate;

    public SequenceService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long next(String sequenceName) {
        Query query = new Query(Criteria.where("_id").is(sequenceName));
        Update update = new Update().inc("seq", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);

        SequenceCounter counter = mongoTemplate.findAndModify(query, update, options, SequenceCounter.class);
        if (counter == null || counter.getSeq() == null) {
            return 1L;
        }
        return counter.getSeq();
    }
}