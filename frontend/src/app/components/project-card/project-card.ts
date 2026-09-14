import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Project, VoteResult } from '../../core/models';
import { VoteButton } from '../vote-button/vote-button';

@Component({
  selector: 'app-project-card',
  imports: [RouterLink, VoteButton],
  templateUrl: './project-card.html',
})
export class ProjectCard {
  readonly project = input.required<Project>();
  readonly showAssignment = input(false);
  readonly voted = output<VoteResult>();
}
