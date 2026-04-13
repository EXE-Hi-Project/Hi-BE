const Chat = require('../models/Chat');

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

    // Save user message
    await Chat.create({ userId: req.user._id, role: 'user', content });

    // Simple AI response (replace with real AI later)
    const aiResponses = [
      'Tôi hiểu cảm giác của bạn. Hãy chăm sóc bản thân thật tốt nhé! 💕',
      'Chu kỳ của bạn trông khá đều đặn. Đó là dấu hiệu tốt! 🌸',
      'Bạn có thể thử uống trà gừng ấm để giảm đau bụng nhé! 🍵',
      'Hãy nghỉ ngơi và lắng nghe cơ thể của bạn. Bạn đang làm rất tốt! ✨',
      'Triệu chứng đó khá phổ biến trong kỳ kinh. Bạn không đơn độc đâu! 🤗',
    ];
    const aiContent = aiResponses[Math.floor(Math.random() * aiResponses.length)];
    const aiMessage = await Chat.create({ userId: req.user._id, role: 'assistant', content: aiContent });

    res.json({ success: true, message: aiMessage });
  } catch (err) { next(err); }
};

module.exports = { getHistory, sendMessage };
