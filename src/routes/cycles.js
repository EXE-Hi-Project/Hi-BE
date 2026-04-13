const express = require('express');
const { getCycles, createCycle, updateCycle, deleteCycle } = require('../controllers/cycleController');
const { protect } = require('../middleware/auth');

const router = express.Router();
router.use(protect);

router.get('/', getCycles);
router.post('/', createCycle);
router.put('/:id', updateCycle);
router.delete('/:id', deleteCycle);

module.exports = router;
