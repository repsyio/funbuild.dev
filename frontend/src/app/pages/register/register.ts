import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected email = '';
  protected password = '';
  protected displayName = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.email, this.password, this.displayName).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Could not create your account.');
      },
    });
  }

  oauthUrl(provider: 'github' | 'google'): string {
    return this.auth.oauthUrl(provider);
  }
}
