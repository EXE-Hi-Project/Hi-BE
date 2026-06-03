package com.hi.api.service;

import com.hi.api.model.SymptomDictionary;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SymptomDictionarySeederTest {

    @Test
    void runningSeederAgainDoesNotCreateDuplicateItems() {
        SymptomDictionaryRepository repository = mock(SymptomDictionaryRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        SymptomDictionarySeeder seeder = new SymptomDictionarySeeder(repository, sequenceService);
        ReflectionTestUtils.setField(seeder, "enabled", true);
        ReflectionTestUtils.setField(seeder, "migrationEnabled", false);
        ReflectionTestUtils.setField(seeder, "migrationDryRun", false);
        when(repository.findByNameIgnoreCase(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            SymptomDictionarySeeder.SeedItem item = SymptomDictionarySeeder.defaultItems().stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                return Optional.empty();
            }
            SymptomDictionary dictionary = new SymptomDictionary();
            dictionary.setName(item.name());
            dictionary.setCategory(item.category());
            return Optional.of(dictionary);
        });

        seeder.run(null);

        verify(sequenceService, never()).next("symptom_dictionaries");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyGenericMoodIsDeactivatedWithoutDeletion() {
        SymptomDictionaryRepository repository = mock(SymptomDictionaryRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        SymptomDictionarySeeder seeder = new SymptomDictionarySeeder(repository, sequenceService);
        ReflectionTestUtils.setField(seeder, "enabled", true);
        ReflectionTestUtils.setField(seeder, "migrationEnabled", false);
        ReflectionTestUtils.setField(seeder, "migrationDryRun", false);
        SymptomDictionary legacy = new SymptomDictionary();
        legacy.setName("Tâm trạng thay đổi");
        legacy.setActive(true);
        when(repository.findByNameIgnoreCase(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            if ("Tâm trạng thay đổi".equals(name)) {
                return Optional.of(legacy);
            }
            SymptomDictionarySeeder.SeedItem item = SymptomDictionarySeeder.defaultItems().stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst()
                    .orElseThrow();
            SymptomDictionary dictionary = new SymptomDictionary();
            dictionary.setName(item.name());
            dictionary.setCategory(item.category());
            dictionary.setActive(true);
            return Optional.of(dictionary);
        });

        seeder.run(null);

        verify(repository).save(legacy);
        org.junit.jupiter.api.Assertions.assertFalse(legacy.getActive());
    }
}
