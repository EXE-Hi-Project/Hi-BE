package com.hi.api.repository;

import com.hi.api.model.SaleCampaign;
import com.hi.api.model.SaleCampaignStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface SaleCampaignRepository extends MongoRepository<SaleCampaign, String> {
    List<SaleCampaign> findByStatusInOrderByStartsAtDesc(Collection<SaleCampaignStatus> statuses);
    List<SaleCampaign> findAllByOrderByStartsAtDesc();
}
