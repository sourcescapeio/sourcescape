const express = require('express');
const logger = require('morgan');

const analyzeRouter = require('./routes/analyze');

const app = express();

app.use(logger('dev'));
app.use('/analyze', analyzeRouter);

module.exports = app;
