const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const userSchema = new mongoose.Schema({
  name: { type: String, required: true, trim: true },
  email: { type: String, required: true, unique: true, lowercase: true, trim: true },
  password: { type: String, required: true, minlength: 6 },
  role: { type: String, enum: ['user', 'admin'], default: 'user' },
  gender: { type: String, enum: ['female', 'male', 'other'], required: true },
  avatar: { type: String, default: '' },
  // Onboarding
  birthDate: { type: Date },
  height: { type: Number },
  weight: { type: Number },
  interests: [{ type: String }],
  goals: [{ type: String }],
  defaultCycleLength: { type: Number, default: 28 },
  defaultPeriodLength: { type: Number, default: 5 },
  lastPeriodDate: { type: Date },
  lastPeriodEndDate: { type: Date },
  irregularCycle: { type: Boolean, default: false },
  // Partner
  partnerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
  partnerCode: { type: String, unique: true, sparse: true },
  // AI preferences
  aiPersonality: { type: String, enum: ['friendly', 'professional', 'caring', 'playful'], default: 'friendly' },
  aiTone: { type: String, enum: ['warm', 'casual', 'formal'], default: 'warm' },
  // Notifications settings
  periodReminder: { type: Boolean, default: true },
  reminderDaysBefore: { type: Number, default: 3 },
  partnerNotifications: { type: Boolean, default: true },
  // Onboarding completion
  onboardingCompleted: { type: Boolean, default: false },
}, { timestamps: true });

userSchema.pre('save', async function (next) {
  if (!this.isModified('password')) return next();
  this.password = await bcrypt.hash(this.password, 12);
  next();
});

userSchema.methods.comparePassword = async function (candidatePassword) {
  return bcrypt.compare(candidatePassword, this.password);
};

userSchema.methods.toJSON = function () {
  const obj = this.toObject();
  delete obj.password;
  return obj;
};

// Generate partner code
userSchema.pre('save', function (next) {
  if (!this.partnerCode) {
    this.partnerCode = Math.random().toString(36).substring(2, 8).toUpperCase();
  }
  next();
});

module.exports = mongoose.model('User', userSchema);
