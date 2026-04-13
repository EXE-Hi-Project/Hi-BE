const User = require('../models/User');
const Cycle = require('../models/Cycle');
const Symptom = require('../models/Symptom');
const Notification = require('../models/Notification');
const Chat = require('../models/Chat');

const toNumber = (value, fallback) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const round2 = (value) => Math.round(value * 100) / 100;

const getRecentMonths = (count = 6) => {
  const now = new Date();
  const months = [];

  for (let i = count - 1; i >= 0; i -= 1) {
    const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    const key = `${year}-${String(month).padStart(2, '0')}`;
    const label = `${String(month).padStart(2, '0')}/${year}`;
    months.push({ year, month, key, label, startDate: date });
  }

  return months;
};

// GET /api/admin/overview
const getOverview = async (req, res, next) => {
  try {
    const months = getRecentMonths(6);
    const firstMonthStart = months[0].startDate;

    const [
      usersTotal,
      usersFemale,
      usersMale,
      adminsTotal,
      cyclesTotal,
      symptomsTotal,
      notificationsTotal,
      unreadNotifications,
      chatMessagesTotal,
      recentUsers,
      usersByMonth,
      chatByMonth,
    ] = await Promise.all([
      User.countDocuments(),
      User.countDocuments({ gender: 'female' }),
      User.countDocuments({ gender: 'male' }),
      User.countDocuments({ role: 'admin' }),
      Cycle.countDocuments(),
      Symptom.countDocuments(),
      Notification.countDocuments(),
      Notification.countDocuments({ read: false }),
      Chat.countDocuments(),
      User.find().select('name email gender role createdAt').sort({ createdAt: -1 }).limit(5),
      User.aggregate([
        { $match: { createdAt: { $gte: firstMonthStart } } },
        {
          $group: {
            _id: {
              year: { $year: '$createdAt' },
              month: { $month: '$createdAt' },
            },
            count: { $sum: 1 },
          },
        },
      ]),
      Chat.aggregate([
        { $match: { createdAt: { $gte: firstMonthStart } } },
        {
          $group: {
            _id: {
              year: { $year: '$createdAt' },
              month: { $month: '$createdAt' },
            },
            count: { $sum: 1 },
          },
        },
      ]),
    ]);

    const monthlyUsersMap = Object.fromEntries(
      usersByMonth.map((item) => [`${item._id.year}-${String(item._id.month).padStart(2, '0')}`, item.count])
    );
    const monthlyChatMap = Object.fromEntries(
      chatByMonth.map((item) => [`${item._id.year}-${String(item._id.month).padStart(2, '0')}`, item.count])
    );

    const paidUserRate = toNumber(process.env.FINANCE_PAID_USER_RATE, 0.15);
    const arpuUsd = toNumber(process.env.FINANCE_ARPU_USD, 4.99);
    const infraCostUsd = toNumber(process.env.FINANCE_INFRA_COST_USD, 30);
    const aiCostPer1kTokens = toNumber(process.env.FINANCE_AI_COST_PER_1K_TOKENS_USD, 0.005);
    const avgMessagesPerConversation = toNumber(process.env.FINANCE_AVG_MESSAGES_PER_CONVERSATION, 10);
    const avgTokensPerConversation = toNumber(process.env.FINANCE_AVG_TOKENS_PER_CONVERSATION, 800);
    const monthlyChurnRate = toNumber(process.env.FINANCE_MONTHLY_CHURN_RATE, 0.04);

    const estimatedConversations = chatMessagesTotal / avgMessagesPerConversation;
    const estimatedAiTokensMonthly = estimatedConversations * avgTokensPerConversation;
    const estimatedAiCostMonthlyUsd = (estimatedAiTokensMonthly / 1000) * aiCostPer1kTokens;
    const estimatedPaidUsers = Math.round(usersTotal * paidUserRate);
    const estimatedMrrUsd = estimatedPaidUsers * arpuUsd;
    const estimatedGrossProfitUsd = estimatedMrrUsd - estimatedAiCostMonthlyUsd - infraCostUsd;
    const estimatedGrossMarginPct = estimatedMrrUsd > 0
      ? (estimatedGrossProfitUsd / estimatedMrrUsd) * 100
      : 0;
    const estimatedLtvUsd = monthlyChurnRate > 0 ? arpuUsd / monthlyChurnRate : 0;

    const monthlyFinancials = months.map((month) => {
      const newUsers = monthlyUsersMap[month.key] || 0;
      const chatMessages = monthlyChatMap[month.key] || 0;
      const newPaidUsers = Math.round(newUsers * paidUserRate);
      const revenueUsd = newPaidUsers * arpuUsd;
      const conversations = chatMessages / avgMessagesPerConversation;
      const aiCostUsd = (conversations * avgTokensPerConversation / 1000) * aiCostPer1kTokens;

      return {
        month: month.label,
        newUsers,
        chatMessages,
        revenueUsd: round2(revenueUsd),
        aiCostUsd: round2(aiCostUsd),
        netUsd: round2(revenueUsd - aiCostUsd),
      };
    });

    res.json({
      success: true,
      overview: {
        usersTotal,
        usersFemale,
        usersMale,
        adminsTotal,
        cyclesTotal,
        symptomsTotal,
        notificationsTotal,
        unreadNotifications,
        chatMessagesTotal,
      },
      financialReport: {
        estimatedPaidUsers,
        estimatedMrrUsd: round2(estimatedMrrUsd),
        estimatedAiCostMonthlyUsd: round2(estimatedAiCostMonthlyUsd),
        infraCostUsd: round2(infraCostUsd),
        estimatedGrossProfitUsd: round2(estimatedGrossProfitUsd),
        estimatedGrossMarginPct: round2(estimatedGrossMarginPct),
        arpuUsd: round2(arpuUsd),
        monthlyChurnRatePct: round2(monthlyChurnRate * 100),
        estimatedLtvUsd: round2(estimatedLtvUsd),
        assumptions: {
          paidUserRate: round2(paidUserRate * 100),
          avgMessagesPerConversation,
          avgTokensPerConversation,
          aiCostPer1kTokens: round2(aiCostPer1kTokens),
        },
      },
      monthlyFinancials,
      recentUsers,
    });
  } catch (err) {
    next(err);
  }
};

// GET /api/admin/users
const getUsers = async (req, res, next) => {
  try {
    const page = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 10, 1), 50);
    const q = (req.query.q || '').trim();
    const role = req.query.role;
    const gender = req.query.gender;

    const filter = {};

    if (q) {
      filter.$or = [
        { name: { $regex: q, $options: 'i' } },
        { email: { $regex: q, $options: 'i' } },
      ];
    }

    if (role && ['user', 'admin'].includes(role)) {
      filter.role = role;
    }

    if (gender && ['female', 'male', 'other'].includes(gender)) {
      filter.gender = gender;
    }

    const [items, total] = await Promise.all([
      User.find(filter)
        .select('name email gender role onboardingCompleted createdAt')
        .sort({ createdAt: -1 })
        .skip((page - 1) * limit)
        .limit(limit),
      User.countDocuments(filter),
    ]);

    res.json({
      success: true,
      items,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    });
  } catch (err) {
    next(err);
  }
};

// PATCH /api/admin/users/:id/role
const updateUserRole = async (req, res, next) => {
  try {
    const { role } = req.body;

    if (!['user', 'admin'].includes(role)) {
      return res.status(400).json({ success: false, message: 'Vai trò không hợp lệ' });
    }

    if (req.user._id.toString() === req.params.id && role !== 'admin') {
      return res.status(400).json({ success: false, message: 'Không thể tự hạ quyền tài khoản hiện tại' });
    }

    const user = await User.findByIdAndUpdate(req.params.id, { role }, { new: true }).select('name email gender role onboardingCompleted createdAt');

    if (!user) {
      return res.status(404).json({ success: false, message: 'Không tìm thấy người dùng' });
    }

    res.json({ success: true, user, message: 'Cập nhật vai trò thành công' });
  } catch (err) {
    next(err);
  }
};

module.exports = { getOverview, getUsers, updateUserRole };
