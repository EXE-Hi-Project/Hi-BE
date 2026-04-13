const Symptom = require('../models/Symptom');

const getSymptoms = async (req, res, next) => {
  try {
    const symptoms = await Symptom.find({ userId: req.user._id }).sort({ date: -1 });
    res.json({ success: true, symptoms });
  } catch (err) { next(err); }
};

const createSymptom = async (req, res, next) => {
  try {
    const { name, severity, date, notes } = req.body;
    if (!name) return res.status(400).json({ success: false, message: 'Tên triệu chứng là bắt buộc' });
    const symptom = await Symptom.create({ userId: req.user._id, name, severity, date, notes });
    res.status(201).json({ success: true, symptom });
  } catch (err) { next(err); }
};

const deleteSymptom = async (req, res, next) => {
  try {
    const symptom = await Symptom.findOneAndDelete({ _id: req.params.id, userId: req.user._id });
    if (!symptom) return res.status(404).json({ success: false, message: 'Không tìm thấy triệu chứng' });
    res.json({ success: true, message: 'Đã xóa triệu chứng' });
  } catch (err) { next(err); }
};

module.exports = { getSymptoms, createSymptom, deleteSymptom };
