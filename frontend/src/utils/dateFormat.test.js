/**
 * @fileoverview Tests for stable UI date/time formatting helpers.
 */
import {describe, expect, it} from 'vitest';
import {formatSessionDateTime} from './dateFormat';

describe('formatSessionDateTime', () => {
    it('formats session timestamps with year first and padded month, day, hour, and minute', () => {
        const date = new Date(2026, 5, 23, 9, 4, 30);

        expect(formatSessionDateTime(date)).toBe('2026/06/23 09:04');
    });

    it('returns an empty string for invalid timestamps', () => {
        expect(formatSessionDateTime('not-a-date')).toBe('');
    });
});
