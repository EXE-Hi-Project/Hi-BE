const buckets = new Map();

const getClientIp = (req) =>
  req.headers['x-forwarded-for']?.split(',')[0]?.trim() ||
  req.ip ||
  req.socket?.remoteAddress ||
  'unknown';

const createRateLimiter = ({
  windowMs,
  max,
  message = 'Bạn thao tác quá nhanh. Vui lòng thử lại sau.',
  keyGenerator = getClientIp,
}) => (req, res, next) => {
  const now = Date.now();
  const key = keyGenerator(req);
  const bucket = buckets.get(key);

  if (!bucket || bucket.resetAt <= now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs });
    res.setHeader('X-RateLimit-Limit', String(max));
    res.setHeader('X-RateLimit-Remaining', String(max - 1));
    return next();
  }

  bucket.count += 1;
  const remaining = Math.max(0, max - bucket.count);
  res.setHeader('X-RateLimit-Limit', String(max));
  res.setHeader('X-RateLimit-Remaining', String(remaining));
  res.setHeader('X-RateLimit-Reset', String(Math.ceil(bucket.resetAt / 1000)));

  if (bucket.count > max) {
    res.setHeader('Retry-After', String(Math.ceil((bucket.resetAt - now) / 1000)));
    return res.status(429).json({ success: false, message });
  }

  next();
};

const authKey = (req) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  return `${getClientIp(req)}:${email || 'anonymous'}`;
};

module.exports = {
  createRateLimiter,
  loginLimiter: createRateLimiter({
    windowMs: 15 * 60 * 1000,
    max: 10,
    message: 'Bạn đăng nhập quá nhiều lần. Vui lòng thử lại sau 15 phút.',
    keyGenerator: authKey,
  }),
  registerLimiter: createRateLimiter({
    windowMs: 60 * 60 * 1000,
    max: 5,
    message: 'Bạn tạo tài khoản quá nhiều lần. Vui lòng thử lại sau 1 giờ.',
    keyGenerator: authKey,
  }),
};
