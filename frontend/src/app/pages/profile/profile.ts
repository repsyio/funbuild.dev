import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { initials } from '../../core/initials';
import { UserService } from '../../core/user.service';

@Component({
  selector: 'app-profile',
  imports: [FormsModule],
  templateUrl: './profile.html',
})
export class Profile {
  protected readonly auth = inject(AuthService);
  private readonly userService = inject(UserService);

  protected readonly initials = initials;
  protected displayName = this.auth.currentUser()?.displayName ?? '';
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saved = signal(false);

  submit(): void {
    this.saving.set(true);
    this.error.set(null);
    this.saved.set(false);
    this.userService.updateMe(this.displayName).subscribe({
      next: (user) => {
        this.saving.set(false);
        this.saved.set(true);
        this.auth.setCurrentUser(user);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message ?? 'Could not update your profile.');
      },
    });
  }
}
