import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Assignment } from '../../core/models';
import { timeLeft } from '../../core/time-left';

@Component({
  selector: 'app-assignment-card',
  imports: [RouterLink],
  templateUrl: './assignment-card.html',
})
export class AssignmentCard {
  readonly assignment = input.required<Assignment>();

  protected readonly statusTagClass: Record<Assignment['status'], string> = {
    ACTIVE: 'tag-accent',
    UPCOMING: 'tag-neutral',
    EXPIRED: 'tag-neutral opacity-60',
  };

  protected readonly timeLeft = timeLeft;
}
