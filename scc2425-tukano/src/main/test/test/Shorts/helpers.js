'use strict';
const fs = require('fs');
const axios = require('axios');

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

async function login(context, events, next) {
    try {
        const response = await axios.post(
            'http://tukano-dns.northeurope.azurecontainer.io:8080/tukano-1/rest/login',
            'username=ana&password=Pass123%21',
            {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
            }
        );
        const setCookieHeader = response.headers['set-cookie'];
        if (setCookieHeader) {
            const sessionMatch = setCookieHeader[0].match(/scc:session=([^;]+)/);
            if (sessionMatch) {
                context.vars.sessionCookie = sessionMatch[1]; // Save session cookie
            } else {
                console.error('scc:session not found in Set-Cookie header');
            }
        } else {
            console.error('No Set-Cookie header found in the response');
        }
    } catch (error) {
        console.error('Login error:', error.message);
    }

    if (next && typeof next === 'function') {
        return next(); // Ensure next() is only called if it's defined
    } else {
        return; // Safely exit if next() is not provided
    }
}

module.exports = {
    extractTokenAna1,
    extractTokenAna2,
    extractTokenBob1,
    loadBinaryPayload,
    login,
};
