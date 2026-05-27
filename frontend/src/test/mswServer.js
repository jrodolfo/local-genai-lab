import { HttpResponse, http } from 'msw';
import { setupServer } from 'msw/node';

export { HttpResponse, http };

export const server = setupServer();

export function sseResponse(events, init = {}) {
  const body = events
    .map((event) => `event: chat\ndata: ${JSON.stringify(event)}\n\n`)
    .join('');

  return new HttpResponse(body, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      ...init.headers
    },
    ...init
  });
}
