import { HttpEventType, HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { apiEnvelopeInterceptor } from './api-envelope.interceptor';

describe('apiEnvelopeInterceptor', () => {
  const request = new HttpRequest('GET', '/api/test');

  it('unwraps successful API responses', () => {
    const response = new HttpResponse({
      status: 200,
      body: { type: 'SUCCESS', data: { id: 42 } },
    });
    const next = vi.fn().mockReturnValue(of(response));

    apiEnvelopeInterceptor(request, next).subscribe((event) => {
      expect(event).toEqual(response.clone({ body: { id: 42 } }));
    });
  });

  it('passes error responses through unchanged', () => {
    const response = new HttpResponse({
      status: 400,
      body: { type: 'ERROR', text: 'Invalid request' },
    });
    const next = vi.fn().mockReturnValue(of(response));

    apiEnvelopeInterceptor(request, next).subscribe((event) => {
      expect(event).toBe(response);
    });
  });

  it('passes non-response HTTP events through unchanged', () => {
    const event = { type: HttpEventType.Sent };
    const next = vi.fn().mockReturnValue(of(event));

    apiEnvelopeInterceptor(request, next).subscribe((result) => {
      expect(result).toBe(event);
    });
  });
});
