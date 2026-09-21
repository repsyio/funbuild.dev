import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth.service';
import { authInterceptor } from './core/auth.interceptor';
import { apiEnvelopeInterceptor } from './core/api-envelope.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, apiEnvelopeInterceptor])),
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
  ],
};
