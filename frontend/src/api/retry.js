function sleep(delayMs) {
  return new Promise((resolve) => window.setTimeout(resolve, delayMs));
}

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

export async function retryAsync(operation, { retries = 0, delayMs = 250 } = {}) {
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
