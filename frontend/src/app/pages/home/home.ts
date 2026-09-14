import { Component, OnInit, inject, signal } from '@angular/core';
import { AssignmentCard } from '../../components/assignment-card/assignment-card';
import { ProjectCard } from '../../components/project-card/project-card';
import { AssignmentService } from '../../core/assignment.service';
import { Assignment, Project, VoteResult } from '../../core/models';
import { ProjectService } from '../../core/project.service';

@Component({
  selector: 'app-home',
  imports: [AssignmentCard, ProjectCard],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private readonly assignmentService = inject(AssignmentService);
  private readonly projectService = inject(ProjectService);

  protected readonly activeAssignments = signal<Assignment[]>([]);
  protected readonly topProjects = signal<Project[]>([]);
  protected readonly loading = signal(true);

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
