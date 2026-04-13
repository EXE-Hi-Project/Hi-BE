const Cycle = require('../models/Cycle');

// GET /api/cycles
const getCycles = async (req, res, next) => {
  try {
    const cycles = await Cycle.find({ userId: req.user._id }).sort({ startDate: -1 });
    res.json({ success: true, cycles });
  } catch (err) { next(err); }
};

// POST /api/cycles
const createCycle = async (req, res, next) => {
  try {
    const { startDate, endDate, cycleLength, notes } = req.body;
    if (!startDate) return res.status(400).json({ success: false, message: 'Ngày bắt đầu là bắt buộc' });

    let periodLength;
    if (endDate) {
      periodLength = Math.round((new Date(endDate) - new Date(startDate)) / (1000 * 60 * 60 * 24)) + 1;
    }

    const cycle = await Cycle.create({ userId: req.user._id, startDate, endDate, cycleLength, periodLength, notes });
    res.status(201).json({ success: true, cycle });
  } catch (err) { next(err); }
};

// PUT /api/cycles/:id
const updateCycle = async (req, res, next) => {
  try {
    const cycle = await Cycle.findOneAndUpdate(
      { _id: req.params.id, userId: req.user._id },
      req.body,
      { new: true }
    );
    if (!cycle) return res.status(404).json({ success: false, message: 'Không tìm thấy chu kỳ' });
    res.json({ success: true, cycle });
  } catch (err) { next(err); }
};

// DELETE /api/cycles/:id
const deleteCycle = async (req, res, next) => {
  try {
    const cycle = await Cycle.findOneAndDelete({ _id: req.params.id, userId: req.user._id });
    if (!cycle) return res.status(404).json({ success: false, message: 'Không tìm thấy chu kỳ' });
    res.json({ success: true, message: 'Đã xóa chu kỳ' });
  } catch (err) { next(err); }
};

module.exports = { getCycles, createCycle, updateCycle, deleteCycle };
