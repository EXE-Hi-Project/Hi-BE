const { BedrockRuntimeClient, InvokeModelCommand } = require('@aws-sdk/client-bedrock-runtime');
const Chat = require('../models/Chat');
const { retrieve } = require('../rag/retrieve');

const bedrockClient = new BedrockRuntimeClient({ region: process.env.AWS_REGION || 'us-east-1' });

const SYSTEM_PROMPT = `Bạn là trợ lý sức khỏe sinh sản thân thiện của ứng dụng Hi. \
Bạn chuyên tư vấn về chu kỳ kinh nguyệt, sức khỏe sinh sản cho cả nam và nữ. \
Trả lời bằng tiếng Việt, ngắn gọn, cảm thông và dựa trên kiến thức y khoa. \
Nếu câu hỏi nghiêm trọng, hãy khuyên người dùng gặp bác sĩ.`;

const getHistory = async (req, res, next) => {
  try {
    const messages = await Chat.find({ userId: req.user._id }).sort({ createdAt: 1 }).limit(100);
    res.json({ success: true, messages });
  } catch (err) { next(err); }
};

const sendMessage = async (req, res, next) => {
  try {
    const { content } = req.body;
    if (!content) return res.status(400).json({ success: false, message: 'Nội dung không được để trống' });

    await Chat.create({ userId: req.user._id, role: 'user', content });

    // Lấy lịch sử gần nhất để context
    const history = await Chat.find({ userId: req.user._id })
      .sort({ createdAt: -1 }).limit(10).lean();
    const messages = history.reverse().map(m => ({ role: m.role, content: m.content }));

    // RAG: tìm tài liệu liên quan
    let systemPrompt = SYSTEM_PROMPT;
    try {
      const ragContext = await retrieve(content);
      if (ragContext) {
        systemPrompt += `\n\nTài liệu tham khảo:\n${ragContext}`;
      }
    } catch (_ragErr) {
      // RAG chưa sẵn sàng — tiếp tục không có context
    }

    const body = JSON.stringify({
      anthropic_version: 'bedrock-2023-05-31',
      max_tokens: 512,
      system: systemPrompt,
      messages,
    });

    const command = new InvokeModelCommand({
      modelId: process.env.BEDROCK_MODEL_ID || 'anthropic.claude-3-haiku-20240307-v1:0',
      contentType: 'application/json',
      accept: 'application/json',
      body,
    });

    const response = await bedrockClient.send(command);
    const result = JSON.parse(Buffer.from(response.body).toString('utf8'));
    const aiContent = result.content[0].text;

    const aiMessage = await Chat.create({ userId: req.user._id, role: 'assistant', content: aiContent });
    res.json({ success: true, message: aiMessage });
  } catch (err) { next(err); }
};

module.exports = { getHistory, sendMessage };
