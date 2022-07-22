const express = require('express');
const logger = require('morgan');

const analyzeRouter = require('./routes/analyze');

const app = express();

app.use(logger('dev'));
app.use('/analyze', analyzeRouter);
app.use('/health', function(req, res) {
    res.status(200).json({status: 'ok'});
});

module.exports = app;
