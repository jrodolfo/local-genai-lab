/**
 * @fileoverview Date formatting helpers for stable UI date/time labels.
 */

/**
 * Formats a session timestamp as `yyyy/mm/dd hh:mm`.
 *
 * @param {string|number|Date} value - Timestamp value accepted by `Date`.
 * @returns {string} Formatted local date/time, or an empty string for invalid values.
 */
export function formatSessionDateTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '';
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    return `${year}/${month}/${day} ${hours}:${minutes}`;
}
