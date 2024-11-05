'use strict';
const fs = require('fs');

function extractTokenAna1(context, events, done) {
    const urlAna1 = new URL(context.vars.fullBlobUrlAna1);
    context.vars.blobUrlTokenAna1 = urlAna1.searchParams.get('token');
    return done();
}

function extractTokenAna2(context, events, done) {
    const urlAna2 = new URL(context.vars.fullBlobUrlAna2);
    context.vars.blobUrlTokenAna2 = urlAna2.searchParams.get('token');
    return done();
}

function extractTokenBob1(context, events, done) {
    const urlBob1 = new URL(context.vars.fullBlobUrlBob1);
    context.vars.blobUrlTokenAna2 = urlBob1.searchParams.get('token');
    return done();
}

function loadBinaryPayload(context, events, done) {
    const binaryData = fs.readFileSync('./Surprised_Pikachu.png');
    context.vars.payload = binaryData;
    return done();
}

module.exports = {
    extractTokenAna1,
    extractTokenAna2,
    extractTokenBob1,
    loadBinaryPayload
};
