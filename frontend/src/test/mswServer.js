/**
 * @fileoverview MSW server setup and SSE (Server-Sent Events) utilities for testing.
 */
import {http, HttpResponse} from 'msw';
import {setupServer} from 'msw/node';

export {HttpResponse, http};

/**
 * MSW server instance for node-based testing.
 * @type {import('msw/node').SetupServerApi}
 */
export const server = setupServer();

/**
 * Formats a JSON object into a Server-Sent Event (SSE) chunk.
 *
 * @param {Object} event - The event payload to format.
 * @returns {string} The formatted SSE chunk.
 */
export function sseEventChunk(event) {
    return `event: chat\ndata: ${JSON.stringify(event)}\n\n`;
}

/**
 * Creates a static MSW HttpResponse that simulates an SSE stream with a predefined list of events.
 *
 * @param {Object[]} events - Array of event objects to include in the stream.
 * @param {Object} [init={}] - Fetch Response initialization options.
 * @returns {HttpResponse} The mock response.
 */
export function sseResponse(events, init = {}) {
    const body = events.map((event) => sseEventChunk(event)).join('');

    return new HttpResponse(body, {
        status: 200,
        headers: {
            'Content-Type': 'text/event-stream',
            ...init.headers
        },
        ...init
    });
}

/**
 * Creates an MSW HttpResponse that simulates an SSE stream with a custom body (e.g., a ReadableStream).
 *
 * @param {ReadableStream|string} body - The stream body.
 * @param {Object} [init={}] - Fetch Response initialization options.
 * @returns {HttpResponse} The mock response.
 */
export function sseStreamResponse(body, init = {}) {
    return new HttpResponse(body, {
        status: 200,
        headers: {
            'Content-Type': 'text/event-stream',
            ...init.headers
        },
        ...init
    });
}
