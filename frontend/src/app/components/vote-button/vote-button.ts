import { Component, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { VoteResult } from '../../core/models';
import { ProjectService } from '../../core/project.service';

@Component({
  selector: 'app-vote-button',
  templateUrl: './vote-button.html',
})
export class VoteButton {
  private readonly projectService = inject(ProjectService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly projectId = input.required<string>();
  readonly voteCount = input.required<number>();
  readonly votedByMe = input.required<boolean>();
  /** 'lg' is used on the project detail page's title row; 'sm' everywhere else. */
  readonly size = input<'sm' | 'lg'>('sm');
  readonly voted = output<VoteResult>();

  protected readonly loading = signal(false);

  toggle(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigateByUrl('/login');
      return;
    }
    this.loading.set(true);
    this.projectService.vote(this.projectId()).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.voted.emit(result);
      },
      error: () => this.loading.set(false),
    });
  }
}
