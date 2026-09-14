import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Assignment } from '../../core/models';

@Component({
  selector: 'app-assignment-card',
  imports: [RouterLink, DatePipe],
  templateUrl: './assignment-card.html',
})
export class AssignmentCard {
  readonly assignment = input.required<Assignment>();

  protected readonly statusBadgeClass: Record<Assignment['status'], string> = {
    ACTIVE: 'badge-success',
    UPCOMING: 'badge-info',
    EXPIRED: 'badge-ghost',
  };
}
