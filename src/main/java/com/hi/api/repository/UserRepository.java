package com.hi.api.repository;

import com.hi.api.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByFacebookId(String facebookId);
    Optional<User> findByPartnerCode(String partnerCode);

    @Query("{ '$or': [ { 'googleId': ?0 }, { 'email': ?1 } ] }")
    Optional<User> findByGoogleIdOrEmail(String googleId, String email);

    @Query("{ '$or': [ { 'facebookId': ?0 }, { 'email': ?1 } ] }")
    Optional<User> findByFacebookIdOrEmail(String facebookId, String email);

    long countByGender(String gender);
    long countByRole(String role);

    @Query(value = "{ 'createdAt': { '$gte': ?0 } }",
           fields = "{ 'name': 1, 'email': 1, 'gender': 1, 'role': 1, 'createdAt': 1 }")
    List<User> findRecentUsersProjected(java.time.Instant since);

    List<User> findTop5ByOrderByCreatedAtDesc();

    @Query("{ 'subscription.stripeCustomerId': ?0 }")
    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    @Query("{ 'subscription.stripeSubscriptionId': ?0 }")
    Optional<User> findByStripeSubscriptionId(String stripeSubscriptionId);
}
