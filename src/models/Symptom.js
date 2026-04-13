const mongoose = require('mongoose');

const symptomSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  date: { type: Date, required: true, default: Date.now },
  name: { type: String, required: true },
  severity: { type: Number, min: 1, max: 5, default: 1 },
  notes: { type: String, default: '' },
}, { timestamps: true });

module.exports = mongoose.model('Symptom', symptomSchema);
