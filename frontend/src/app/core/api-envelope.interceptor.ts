import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { map } from 'rxjs';

interface ApiEnvelope<T> {
  type: 'SUCCESS' | 'ERROR' | 'WARNING';
  data: T;
}

export const apiEnvelopeInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    map((event) => {
      const response = event instanceof HttpResponse ? event : null;
      const body = response?.body as Partial<ApiEnvelope<unknown>> | null;
      if (!response || !body || body.type !== 'SUCCESS') {
        return event;
      }
      const envelope = body as ApiEnvelope<unknown>;
      return response.clone({ body: envelope.data });
    }),
  );
