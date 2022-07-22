const express = require('express');
const bodyParser = require('body-parser');
const router = express.Router();

const parser = require('@typescript-eslint/typescript-estree');

/* GET home page. */
router.post('/', 
  bodyParser.text(),
  function(req, res, next) {
    let parsed;
    try {
      const parsed = parser.parse(req.body, {loc: true, range: true, jsx: true});
      res.json(parsed);
    } catch (err) {
      console.error(err);
      console.warn(req.body);
      res.status(400).json({error: 'Error parsing'});
    }
  }
);

module.exports = router;
