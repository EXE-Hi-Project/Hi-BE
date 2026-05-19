const VALID_GENDERS = ['female', 'male', 'other'];
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const normalizeEmail = (email) => String(email || '').trim().toLowerCase();
const normalizeName = (name) => String(name || '').trim().replace(/\s+/g, ' ');

const isStrongEnoughPassword = (password) => typeof password === 'string' && password.length >= 8;

const validateRegisterInput = ({ name, email, password, gender }) => {
  const errors = [];
  const normalizedName = normalizeName(name);
  const normalizedEmail = normalizeEmail(email);

  if (!normalizedName || normalizedName.length < 2) {
    errors.push('Tên phải có ít nhất 2 ký tự');
  }

  if (!normalizedEmail || !EMAIL_REGEX.test(normalizedEmail)) {
    errors.push('Email không hợp lệ');
  }

  if (!isStrongEnoughPassword(password)) {
    errors.push('Mật khẩu phải có ít nhất 8 ký tự');
  }

  if (!VALID_GENDERS.includes(gender)) {
    errors.push('Giới tính không hợp lệ');
  }

  return {
    isValid: errors.length === 0,
    message: errors[0],
    values: {
      name: normalizedName,
      email: normalizedEmail,
      password,
      gender,
    },
  };
};

const validateLoginInput = ({ email, password }) => {
  const normalizedEmail = normalizeEmail(email);

  if (!normalizedEmail || !EMAIL_REGEX.test(normalizedEmail)) {
    return { isValid: false, message: 'Email không hợp lệ', values: { email: normalizedEmail, password } };
  }

  if (!password) {
    return { isValid: false, message: 'Vui lòng nhập mật khẩu', values: { email: normalizedEmail, password } };
  }

  return { isValid: true, values: { email: normalizedEmail, password } };
};

module.exports = {
  VALID_GENDERS,
  normalizeEmail,
  validateLoginInput,
  validateRegisterInput,
};
