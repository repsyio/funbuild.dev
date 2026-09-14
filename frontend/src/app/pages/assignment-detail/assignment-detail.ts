import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProjectCard } from '../../components/project-card/project-card';
import { ProjectForm } from '../../components/project-form/project-form';
import { AssignmentService } from '../../core/assignment.service';
import { AuthService } from '../../core/auth.service';
import { Assignment, Project, VoteResult } from '../../core/models';
import { ProjectService } from '../../core/project.service';
import { timeLeft } from '../../core/time-left';

@Component({
  selector: 'app-assignment-detail',
  imports: [DatePipe, RouterLink, ProjectCard, ProjectForm],
  templateUrl: './assignment-detail.html',
})
export class AssignmentDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly assignmentService = inject(AssignmentService);
  private readonly projectService = inject(ProjectService);
  protected readonly auth = inject(AuthService);

  protected readonly assignment = signal<Assignment | null>(null);
  protected readonly projects = signal<Project[]>([]);
  protected readonly loading = signal(true);
  protected readonly showForm = signal(false);

  protected readonly timeLeft = timeLeft;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.assignmentService.get(id).subscribe((assignment) => {
      this.assignment.set(assignment);
      this.loading.set(false);
    });
    this.loadProjects(id);
  }

  private loadProjects(assignmentId: number): void {
    this.projectService.list(assignmentId).subscribe((projects) => this.projects.set(projects));
  }

  onSubmitted(): void {
    this.showForm.set(false);
    this.loadProjects(this.assignment()!.id);
  }

  onVoted(project: Project, result: VoteResult): void {
    this.projects.set(this.projects().map((p) => (p.id === project.id ? { ...p, ...result } : p)));
  }
}
