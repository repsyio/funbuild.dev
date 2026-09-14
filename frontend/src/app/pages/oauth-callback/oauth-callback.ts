import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-oauth-callback',
  imports: [RouterLink],
  templateUrl: './oauth-callback.html',
})
export class OauthCallback implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly error = signal(false);

  ngOnInit(): void {
    const token = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token');
    if (!token) {
      this.error.set(true);
      return;
    }
    this.auth.applyToken(token).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => this.error.set(true),
    });
  }
}
