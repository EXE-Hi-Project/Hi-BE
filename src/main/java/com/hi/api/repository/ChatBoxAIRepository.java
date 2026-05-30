package com.hi.api.repository;

import com.hi.api.model.ChatMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
@Repository
public interface ChatBoxAIRepository extends MongoRepository<ChatMessage, String>, ChatMemoryRepository {


    @Override
    default List<String> findConversationIds() {
        return List.of();
    }

    @Override
    default List<Message> findByConversationId(String conversationId) {
        return List.of();
    }

    @Override
    default void saveAll(String conversationId, List<Message> messages) {


    }

    @Override
    default void deleteByConversationId(String conversationId) {

    }

    @Override
    default <S extends ChatMessage> S insert(S entity) {
        return null;
    }

    @Override
    default <S extends ChatMessage> List<S> insert(Iterable<S> entities) {
        return List.of();
    }

    @Override
    default <S extends ChatMessage> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    default <S extends ChatMessage> List<S> findAll(Example<S> example) {
        return List.of();
    }

    @Override
    default <S extends ChatMessage> List<S> findAll(Example<S> example, Sort sort) {
        return List.of();
    }

    @Override
    default <S extends ChatMessage> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }

    @Override
    default <S extends ChatMessage> long count(Example<S> example) {
        return 0;
    }

    @Override
    default <S extends ChatMessage> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    default <S extends ChatMessage, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    default <S extends ChatMessage> S save(S entity) {
        return null;
    }

    @Override
    default <S extends ChatMessage> List<S> saveAll(Iterable<S> entities) {
        return List.of();
    }

    @Override
    default Optional<ChatMessage> findById(String s) {
        return Optional.empty();
    }

    @Override
    default boolean existsById(String s) {
        return false;
    }

    @Override
    default List<ChatMessage> findAll() {
        return List.of();
    }

    @Override
    default List<ChatMessage> findAllById(Iterable<String> strings) {
        return List.of();
    }

    @Override
    default long count() {
        return 0;
    }

    @Override
    default void deleteById(String s) {

    }

    @Override
    default void delete(ChatMessage entity) {

    }

    @Override
    default void deleteAllById(Iterable<? extends String> strings) {

    }

    @Override
    default void deleteAll(Iterable<? extends ChatMessage> entities) {

    }

    @Override
    default void deleteAll() {

    }

    @Override
    default List<ChatMessage> findAll(Sort sort) {
        return List.of();
    }

    @Override
    default Page<ChatMessage> findAll(Pageable pageable) {
        return null;
    }
}
