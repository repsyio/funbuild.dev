import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AssignmentCard } from '../../components/assignment-card/assignment-card';
import { VoteButton } from '../../components/vote-button/vote-button';
import { AssignmentService } from '../../core/assignment.service';
import { initials } from '../../core/initials';
import { Assignment, Project, VoteResult } from '../../core/models';
import { ProjectService } from '../../core/project.service';

@Component({
  selector: 'app-home',
  imports: [AssignmentCard, VoteButton, RouterLink],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private readonly assignmentService = inject(AssignmentService);
  private readonly projectService = inject(ProjectService);

  protected readonly activeAssignments = signal<Assignment[]>([]);
  protected readonly topProjects = signal<Project[]>([]);
  protected readonly loading = signal(true);

  protected readonly heroHeadline = computed(() => {
    const n = this.activeAssignments().length;
    if (n === 0) {
      return 'No assignments are open right now. Check back soon.';
    }
    return `${n} assignment${n === 1 ? ' is' : 's are'} live. Ship one, climb the board.`;
  });

  protected readonly initials = initials;

  ngOnInit(): void {
    this.assignmentService.list('active').subscribe((assignments) => this.activeAssignments.set(assignments));
    this.projectService.top(9).subscribe((projects) => {
      this.topProjects.set(projects);
      this.loading.set(false);
    });
  }

  onVoted(project: Project, result: VoteResult): void {
    this.topProjects.set(
      this.topProjects().map((p) => (p.id === project.id ? { ...p, ...result } : p)),
    );
  }
}
