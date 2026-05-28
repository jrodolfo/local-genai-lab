/**
 * @fileoverview Utility for retrying asynchronous operations with exponential-like backoff (fixed delay).
 */

/**
 * Pauses execution for a specified duration.
 *
 * @param {number} delayMs - The delay in milliseconds.
 * @returns {Promise<void>}
 */
function sleep(delayMs) {
    return new Promise((resolve) => window.setTimeout(resolve, delayMs));
}

/**
 * Determines if an error should trigger a retry.
 * Retries on network errors or fetch failures.
 *
 * @param {Error} error - The error to check.
 * @returns {boolean} True if the operation should be retried.
 */
function shouldRetry(error) {
    if (!error) {
        return false;
    }
    if (error instanceof TypeError) {
        return true;
    }
    const message = String(error.message || '').toLowerCase();
    return message.includes('failed to fetch')
        || message.includes('fetch failed')
        || message.includes('networkerror')
        || message.includes('network error');
}

/**
 * Retries an asynchronous operation until it succeeds or the maximum number of retries is reached.
 *
 * @param {Function} operation - The async function to retry.
 * @param {Object} [options] - Retry options.
 * @param {number} [options.retries=0] - Number of retry attempts.
 * @param {number} [options.delayMs=250] - Delay between attempts in milliseconds.
 * @returns {Promise<*>} The result of the operation.
 * @throws {Error} The last error encountered if all attempts fail.
 */
export async function retryAsync(operation, {retries = 0, delayMs = 250} = {}) {
    let attempt = 0;
    let lastError;

    while (attempt <= retries) {
        try {
            return await operation();
        } catch (error) {
            lastError = error;
            if (attempt === retries || !shouldRetry(error)) {
                break;
            }
            await sleep(delayMs);
            attempt += 1;
        }
    }

    throw lastError;
}
