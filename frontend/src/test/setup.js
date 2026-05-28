/**
 * @fileoverview Global test setup for Vitest.
 * Configures MSW server lifecycle and extends Jest DOM matchers.
 */
import '@testing-library/jest-dom/vitest';
import {afterAll, afterEach, beforeAll} from 'vitest';
import {server} from './mswServer';

beforeAll(() => {
    server.listen({onUnhandledRequest: 'error'});
});

afterEach(() => {
    server.resetHandlers();
});

afterAll(() => {
    server.close();
});
