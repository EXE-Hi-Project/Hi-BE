package com.hi.api.service;

import com.hi.api.model.SymptomCategory;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(50)
public class SymptomDictionarySeeder implements ApplicationRunner {

    private final SymptomDictionaryRepository repository;
    private final SequenceService sequenceService;

    @Value("${app.symptom-dictionary.seed-enabled:true}")
    private boolean enabled;

    @Value("${app.migration.health-data.enabled:false}")
    private boolean migrationEnabled;

    @Value("${app.migration.health-data.dry-run:true}")
    private boolean migrationDryRun;

    public SymptomDictionarySeeder(SymptomDictionaryRepository repository, SequenceService sequenceService) {
        this.repository = repository;
        this.sequenceService = sequenceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || (migrationEnabled && migrationDryRun)) {
            return;
        }
        for (SeedItem item : defaultItems()) {
            repository.findByNameIgnoreCase(item.name()).ifPresentOrElse(dictionary -> {
                if (!item.category().equals(dictionary.getCategory()) || !Boolean.TRUE.equals(dictionary.getActive())) {
                    dictionary.setCategory(item.category());
                    dictionary.setActive(true);
                    repository.save(dictionary);
                }
            }, () -> {
                SymptomDictionary dictionary = new SymptomDictionary();
                dictionary.setId(sequenceService.next("symptom_dictionaries"));
                dictionary.setName(item.name());
                dictionary.setCategory(item.category());
                dictionary.setIconUrl("");
                dictionary.setActive(true);
                repository.save(dictionary);
            });
        }
        repository.findByNameIgnoreCase("Tâm trạng thay đổi").ifPresent(dictionary -> {
            if (Boolean.TRUE.equals(dictionary.getActive())) {
                dictionary.setActive(false);
                repository.save(dictionary);
            }
        });
    }

    public static List<SeedItem> defaultItems() {
        return List.of(
                new SeedItem("Đau bụng", SymptomCategory.PHYSICAL),
                new SeedItem("Đau đầu", SymptomCategory.PHYSICAL),
                new SeedItem("Mệt mỏi", SymptomCategory.PHYSICAL),
                new SeedItem("Nổi mụn", SymptomCategory.PHYSICAL),
                new SeedItem("Đau lưng", SymptomCategory.PHYSICAL),
                new SeedItem("Ngực đau", SymptomCategory.PHYSICAL),
                new SeedItem("Mất ngủ", SymptomCategory.PHYSICAL),
                new SeedItem("Chóng mặt", SymptomCategory.PHYSICAL),
                new SeedItem("Thèm ăn", SymptomCategory.PHYSICAL),
                new SeedItem("Ngứa âm đạo", SymptomCategory.PHYSICAL),
                new SeedItem("Khô âm đạo", SymptomCategory.PHYSICAL),
                new SeedItem("Bình tĩnh", SymptomCategory.EMOTIONAL),
                new SeedItem("Vui vẻ", SymptomCategory.EMOTIONAL),
                new SeedItem("Mạnh mẽ", SymptomCategory.EMOTIONAL),
                new SeedItem("Phấn chấn", SymptomCategory.EMOTIONAL),
                new SeedItem("Thất thường", SymptomCategory.EMOTIONAL),
                new SeedItem("Bực bội", SymptomCategory.EMOTIONAL),
                new SeedItem("Buồn", SymptomCategory.EMOTIONAL),
                new SeedItem("Lo lắng", SymptomCategory.EMOTIONAL),
                new SeedItem("Thiếu năng lượng", SymptomCategory.EMOTIONAL),
                new SeedItem("Buồn nôn", SymptomCategory.OTHER),
                new SeedItem("Đầy hơi", SymptomCategory.OTHER),
                new SeedItem("Táo bón", SymptomCategory.OTHER),
                new SeedItem("Tiêu chảy", SymptomCategory.OTHER),
                new SeedItem("Không có dịch", SymptomCategory.FLUID),
                new SeedItem("Trắng đục", SymptomCategory.FLUID),
                new SeedItem("Ẩm ướt", SymptomCategory.FLUID),
                new SeedItem("Dạng dính", SymptomCategory.FLUID),
                new SeedItem("Như lòng trắng trứng", SymptomCategory.FLUID),
                new SeedItem("Dạng đốm", SymptomCategory.FLUID),
                new SeedItem("Bất thường", SymptomCategory.FLUID),
                new SeedItem("Trắng, vón cục", SymptomCategory.FLUID),
                new SeedItem("Xám", SymptomCategory.FLUID)
        );
    }

    public record SeedItem(String name, SymptomCategory category) {
    }
}
