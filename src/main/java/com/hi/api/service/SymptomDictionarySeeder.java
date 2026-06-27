package com.hi.api.service;

import com.hi.api.model.SymptomCategory;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        correctLegacyNames();
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
        deactivateDeprecatedMood();
    }

    private void correctLegacyNames() {
        legacyNameCorrections().forEach((legacyName, canonicalName) ->
                repository.findByNameIgnoreCase(legacyName).ifPresent(legacy ->
                        repository.findByNameIgnoreCase(canonicalName).ifPresentOrElse(canonical -> {
                            if (!canonical.getId().equals(legacy.getId()) && Boolean.TRUE.equals(legacy.getActive())) {
                                legacy.setActive(false);
                                repository.save(legacy);
                            }
                        }, () -> {
                            legacy.setName(canonicalName);
                            legacy.setActive(true);
                            repository.save(legacy);
                        })));
    }

    private void deactivateDeprecatedMood() {
        for (String name : List.of("Tâm trạng thay đổi", "TÃ¢m tráº¡ng thay Ä‘á»•i")) {
            repository.findByNameIgnoreCase(name).ifPresent(dictionary -> {
                if (Boolean.TRUE.equals(dictionary.getActive())) {
                    dictionary.setActive(false);
                    repository.save(dictionary);
                }
            });
        }
    }

    private static Map<String, String> legacyNameCorrections() {
        Map<String, String> corrections = new LinkedHashMap<>();
        corrections.put("Äau bá»¥ng", "Đau bụng");
        corrections.put("Äau Ä‘áº§u", "Đau đầu");
        corrections.put("Má»‡t má»i", "Mệt mỏi");
        corrections.put("Ná»•i má»¥n", "Nổi mụn");
        corrections.put("Äau lÆ°ng", "Đau lưng");
        corrections.put("Ngá»±c Ä‘au", "Ngực đau");
        corrections.put("Máº¥t ngá»§", "Mất ngủ");
        corrections.put("ChÃ³ng máº·t", "Chóng mặt");
        corrections.put("ThÃ¨m Äƒn", "Thèm ăn");
        corrections.put("Ngá»©a Ã¢m Ä‘áº¡o", "Ngứa âm đạo");
        corrections.put("KhÃ´ Ã¢m Ä‘áº¡o", "Khô âm đạo");
        corrections.put("BÃ¬nh tÄ©nh", "Bình tĩnh");
        corrections.put("Vui váº»", "Vui vẻ");
        corrections.put("Máº¡nh máº½", "Mạnh mẽ");
        corrections.put("Pháº¥n cháº¥n", "Phấn chấn");
        corrections.put("Tháº¥t thÆ°á»ng", "Thất thường");
        corrections.put("Bá»±c bá»™i", "Bực bội");
        corrections.put("Buá»“n", "Buồn");
        corrections.put("Lo láº¯ng", "Lo lắng");
        corrections.put("Thiáº¿u nÄƒng lÆ°á»£ng", "Thiếu năng lượng");
        corrections.put("Buá»“n nÃ´n", "Buồn nôn");
        corrections.put("Äáº§y hÆ¡i", "Đầy hơi");
        corrections.put("TÃ¡o bÃ³n", "Táo bón");
        corrections.put("TiÃªu cháº£y", "Tiêu chảy");
        corrections.put("KhÃ´ng cÃ³ dá»‹ch", "Không có dịch");
        corrections.put("Tráº¯ng Ä‘á»¥c", "Trắng đục");
        corrections.put("áº¨m Æ°á»›t", "Ẩm ướt");
        corrections.put("Dáº¡ng dÃ­nh", "Dạng dính");
        corrections.put("NhÆ° lÃ²ng tráº¯ng trá»©ng", "Như lòng trắng trứng");
        corrections.put("Dáº¡ng Ä‘á»‘m", "Dạng đốm");
        corrections.put("Báº¥t thÆ°á»ng", "Bất thường");
        corrections.put("Tráº¯ng, vÃ³n cá»¥c", "Trắng, vón cục");
        corrections.put("XÃ¡m", "Xám");
        return corrections;
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
