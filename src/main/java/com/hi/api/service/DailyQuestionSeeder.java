package com.hi.api.service;

import com.hi.api.model.DailyQuestion;
import com.hi.api.repository.DailyQuestionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DailyQuestionSeeder implements ApplicationRunner {

    private final DailyQuestionRepository repository;

    public DailyQuestionSeeder(DailyQuestionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.countByActiveTrue() >= 90) return;

        Map<String, List<String>> questions = new LinkedHashMap<>();
        questions.put("Kết nối", List.of(
                "Điều nhỏ nhất Người ấy làm gần đây khiến bạn vui là gì?",
                "Khoảnh khắc nào trong tuần này khiến bạn cảm thấy hai người gần nhau hơn?",
                "Bạn muốn dành thêm thời gian cùng Người ấy cho hoạt động nào?",
                "Một thói quen của Người ấy mà bạn thấy đáng yêu là gì?",
                "Khi nghĩ về hai người, điều gì khiến bạn cảm thấy an tâm nhất?",
                "Bạn muốn cùng Người ấy tạo thêm kỷ niệm nào trong tháng này?",
                "Một điều bạn muốn cảm ơn Người ấy hôm nay là gì?",
                "Lúc nào bạn cảm thấy được Người ấy thấu hiểu nhất?",
                "Một điểm giống nhau của hai người mà bạn trân trọng là gì?",
                "Một điểm khác nhau giúp hai người bổ sung cho nhau là gì?",
                "Nếu có một buổi tối hoàn toàn rảnh, bạn muốn hai người làm gì?",
                "Điều gì giúp bạn dễ mở lòng với Người ấy hơn?",
                "Một bài hát gợi bạn nhớ đến Người ấy là bài nào và vì sao?",
                "Bạn muốn hai người duy trì nghi thức nhỏ nào mỗi tuần?",
                "Ba từ bạn dùng để mô tả mối quan hệ hiện tại là gì?"
        ));
        questions.put("Giao tiếp", List.of(
                "Khi căng thẳng, bạn muốn được lắng nghe hay cùng tìm giải pháp?",
                "Có chủ đề nào bạn muốn hai người nói với nhau thường xuyên hơn?",
                "Bạn thích Người ấy góp ý cho mình theo cách nào?",
                "Khi bất đồng, điều gì giúp bạn bình tĩnh lại nhanh nhất?",
                "Một câu nói nào từ Người ấy thường giúp bạn cảm thấy tốt hơn?",
                "Bạn muốn được hỏi han như thế nào sau một ngày dài?",
                "Điều gì khiến bạn cảm thấy an toàn khi chia sẻ cảm xúc?",
                "Khi cần không gian riêng, bạn muốn nói với Người ấy ra sao?",
                "Một hiểu lầm nhỏ hai người có thể phòng tránh bằng cách nào?",
                "Bạn muốn Người ấy biết điều gì về cách bạn thể hiện tình cảm?",
                "Bạn dễ nói chuyện nhất vào thời điểm nào trong ngày?",
                "Khi buồn, bạn muốn Người ấy ở cạnh hay cho bạn thời gian?",
                "Một điều bạn muốn cả hai cùng thực hành để giao tiếp tốt hơn là gì?",
                "Bạn muốn hai người xử lý việc quên lời hứa nhỏ như thế nào?",
                "Dấu hiệu nào cho thấy bạn đang cần được quan tâm?"
        ));
        questions.put("Quan tâm", List.of(
                "Hành động chăm sóc nhỏ nào có ý nghĩa nhất với bạn?",
                "Hôm nay Người ấy có thể làm gì để bạn nhẹ lòng hơn?",
                "Bạn thích được động viên bằng lời nói hay hành động?",
                "Khi mệt, món ăn hoặc thức uống nào khiến bạn dễ chịu?",
                "Bạn muốn nhận một lời nhắn quan tâm vào thời điểm nào?",
                "Điều gì khiến bạn cảm thấy được tôn trọng trong mối quan hệ?",
                "Một việc nhà nhỏ nào hai người có thể chia sẻ công bằng hơn?",
                "Bạn muốn Người ấy nhắc nhở điều gì mà không tạo áp lực?",
                "Khi bạn im lặng, Người ấy nên làm gì trước tiên?",
                "Một cách nghỉ ngơi bạn muốn hai người thử cùng nhau là gì?",
                "Bạn cảm nhận tình yêu rõ nhất qua lời nói, thời gian hay hành động?",
                "Điều gì giúp bạn phục hồi năng lượng sau một ngày khó khăn?",
                "Bạn muốn hai người chăm sóc giấc ngủ của nhau như thế nào?",
                "Một lời khen nào khiến bạn cảm thấy chân thành nhất?",
                "Hôm nay bạn muốn gửi lời chúc gì tới Người ấy?"
        ));
        questions.put("Kỷ niệm", List.of(
                "Kỷ niệm đầu tiên về Người ấy mà bạn vẫn nhớ rõ là gì?",
                "Buổi hẹn nào của hai người khiến bạn muốn trải nghiệm lại?",
                "Một lần Người ấy làm bạn bất ngờ theo cách tích cực là khi nào?",
                "Bức ảnh chung nào khiến bạn mỉm cười nhiều nhất?",
                "Một chuyến đi ngắn đáng nhớ của hai người là gì?",
                "Món quà nhỏ nào từ Người ấy có ý nghĩa đặc biệt với bạn?",
                "Khoảnh khắc nào khiến bạn nhận ra mình có thể tin tưởng Người ấy?",
                "Một khó khăn hai người từng vượt qua cùng nhau là gì?",
                "Món ăn nào gắn với một kỷ niệm vui của hai người?",
                "Lần đầu hai người cười thật nhiều cùng nhau là khi nào?",
                "Một nơi quen thuộc nào mang ý nghĩa riêng với hai người?",
                "Bạn muốn lưu giữ câu chuyện nào để kể lại sau nhiều năm?",
                "Một ngày bình thường nhưng đáng nhớ của hai người là ngày nào?",
                "Điều gì đã thay đổi tích cực từ khi hai người ở bên nhau?",
                "Kỷ niệm nào nhắc bạn rằng hai người là một đội?"
        ));
        questions.put("Tương lai", List.of(
                "Một mục tiêu chung bạn muốn hai người hoàn thành trong ba tháng tới?",
                "Bạn hình dung một cuối tuần lý tưởng của hai người trong tương lai thế nào?",
                "Kỹ năng nào bạn muốn hai người cùng học?",
                "Một nơi bạn muốn cùng Người ấy đến trong tương lai?",
                "Thói quen tài chính lành mạnh nào hai người có thể xây dựng?",
                "Bạn muốn tổ ấm tương lai mang cảm giác như thế nào?",
                "Một truyền thống riêng bạn muốn hai người tạo ra là gì?",
                "Bạn muốn hai người hỗ trợ sự nghiệp của nhau ra sao?",
                "Mục tiêu sức khỏe chung nào phù hợp với cả hai?",
                "Điều gì bạn muốn giữ nguyên dù cuộc sống thay đổi?",
                "Một dự án nhỏ hai người có thể cùng bắt đầu là gì?",
                "Bạn muốn cân bằng thời gian riêng và thời gian chung thế nào?",
                "Trong một năm tới, điều gì sẽ khiến bạn tự hào về hai người?",
                "Bạn muốn hai người chuẩn bị cho những giai đoạn bận rộn ra sao?",
                "Một giá trị bạn muốn luôn hiện diện trong tương lai của hai người?"
        ));
        questions.put("Sức khỏe", List.of(
                "Khi không khỏe, bạn muốn Người ấy hỏi han như thế nào?",
                "Một thói quen sức khỏe bạn muốn Người ấy cùng đồng hành là gì?",
                "Bạn muốn được nhắc uống nước, nghỉ ngơi hay vận động theo cách nào?",
                "Điều gì khiến bạn ngại chia sẻ khi cơ thể không thoải mái?",
                "Bạn muốn hai người hỗ trợ nhau đi khám sức khỏe định kỳ ra sao?",
                "Khi đau hoặc mệt, bạn thích được giúp việc gì nhất?",
                "Một thay đổi nhỏ nào có thể giúp giấc ngủ của cả hai tốt hơn?",
                "Bạn muốn nói về sức khỏe sinh sản với Người ấy trong không khí thế nào?",
                "Dấu hiệu nào cho thấy bạn cần nghỉ ngơi thay vì cố gắng thêm?",
                "Hoạt động nhẹ nhàng nào giúp bạn cảm thấy khỏe hơn?",
                "Bạn muốn Người ấy phản ứng thế nào khi bạn chia sẻ triệu chứng nhạy cảm?",
                "Một bữa ăn lành mạnh hai người đều thích là gì?",
                "Điều gì giúp bạn cảm thấy không bị phán xét khi nói về cơ thể?",
                "Bạn muốn hai người cùng theo dõi mục tiêu sức khỏe nào?",
                "Một cam kết chăm sóc bản thân bạn muốn bắt đầu từ tuần này?"
        ));

        List<DailyQuestion> seeded = new ArrayList<>();
        int order = 1;
        for (Map.Entry<String, List<String>> entry : questions.entrySet()) {
            for (String prompt : entry.getValue()) {
                DailyQuestion question = new DailyQuestion();
                question.setId("question-" + order);
                question.setCategory(entry.getKey());
                question.setPrompt(prompt);
                question.setDisplayOrder(order++);
                question.setActive(true);
                seeded.add(question);
            }
        }
        repository.saveAll(seeded);
    }
}
