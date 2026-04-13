const mongoose = require('mongoose');

const cycleSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  startDate: { type: Date, required: true },
  endDate: { type: Date },
  periodLength: { type: Number },
  cycleLength: { type: Number, default: 28 },
  notes: { type: String, default: '' },
  symptoms: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Symptom' }],
}, { timestamps: true });

module.exports = mongoose.model('Cycle', cycleSchema);
