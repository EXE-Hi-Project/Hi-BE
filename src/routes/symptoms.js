const express = require('express');
const { getSymptoms, createSymptom, deleteSymptom } = require('../controllers/symptomController');
const { protect } = require('../middleware/auth');

const router = express.Router();
router.use(protect);

router.get('/', getSymptoms);
router.post('/', createSymptom);
router.delete('/:id', deleteSymptom);

module.exports = router;
