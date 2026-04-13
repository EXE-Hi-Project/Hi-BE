const User = require('../models/User');
const Cycle = require('../models/Cycle');

// GET /api/users/profile
const getProfile = async (req, res) => {
  res.json({ success: true, user: req.user });
};

// PUT /api/users/profile
const updateProfile = async (req, res, next) => {
  try {
    const allowed = ['name', 'gender', 'birthDate', 'height', 'weight', 'interests', 'goals', 'defaultCycleLength', 'defaultPeriodLength', 'lastPeriodDate', 'lastPeriodEndDate', 'irregularCycle', 'aiPersonality', 'aiTone', 'periodReminder', 'reminderDaysBefore', 'partnerNotifications', 'onboardingCompleted'];
    const updates = {};
    allowed.forEach(f => { if (req.body[f] !== undefined) updates[f] = req.body[f]; });

    const user = await User.findByIdAndUpdate(req.user._id, updates, { new: true });
    res.json({ success: true, user });
  } catch (err) { next(err); }
};

// POST /api/users/connect-partner
const connectPartner = async (req, res, next) => {
  try {
    const { partnerCode } = req.body;
    if (!partnerCode) return res.status(400).json({ success: false, message: 'Vui lòng nhập mã kết nối' });

    const partner = await User.findOne({ partnerCode: partnerCode.toUpperCase() });
    if (!partner) return res.status(404).json({ success: false, message: 'Không tìm thấy đối tác với mã này' });
    if (partner._id.toString() === req.user._id.toString())
      return res.status(400).json({ success: false, message: 'Không thể kết nối với chính mình' });

    await User.findByIdAndUpdate(req.user._id, { partnerId: partner._id });
    await User.findByIdAndUpdate(partner._id, { partnerId: req.user._id });

    res.json({ success: true, message: 'Kết nối thành công', partner: { name: partner.name, email: partner.email } });
  } catch (err) { next(err); }
};

// DELETE /api/users/disconnect-partner
const disconnectPartner = async (req, res, next) => {
  try {
    const user = await User.findById(req.user._id);
    if (user.partnerId) {
      await User.findByIdAndUpdate(user.partnerId, { partnerId: null });
    }
    await User.findByIdAndUpdate(req.user._id, { partnerId: null });
    res.json({ success: true, message: 'Đã ngắt kết nối với đối tác' });
  } catch (err) { next(err); }
};

// GET /api/users/partner-cycles (for male user)
const getPartnerCycles = async (req, res, next) => {
  try {
    if (!req.user.partnerId) return res.status(404).json({ success: false, message: 'Chưa kết nối với đối tác' });
    const cycles = await Cycle.find({ userId: req.user.partnerId }).sort({ startDate: -1 });
    res.json({ success: true, cycles });
  } catch (err) { next(err); }
};

module.exports = { getProfile, updateProfile, connectPartner, disconnectPartner, getPartnerCycles };
