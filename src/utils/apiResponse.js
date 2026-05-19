const sendSuccess = (res, { statusCode = 200, message = 'Thành công', data = {} } = {}) => {
  res.status(statusCode).json({
    success: true,
    message,
    data,
    ...data,
  });
};

const sendError = (res, { statusCode = 400, message = 'Yêu cầu không hợp lệ' } = {}) => {
  res.status(statusCode).json({
    success: false,
    message,
  });
};

module.exports = { sendSuccess, sendError };
