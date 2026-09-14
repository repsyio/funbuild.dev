import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProjectForm } from '../../components/project-form/project-form';
import { VoteButton } from '../../components/vote-button/vote-button';
import { AuthService } from '../../core/auth.service';
import { Project, VoteResult } from '../../core/models';
import { ProjectService } from '../../core/project.service';

@Component({
  selector: 'app-project-detail',
  imports: [RouterLink, DatePipe, VoteButton, ProjectForm],
  templateUrl: './project-detail.html',
})
export class ProjectDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly projectService = inject(ProjectService);
  protected readonly auth = inject(AuthService);

  protected readonly project = signal<Project | null>(null);
  protected readonly loading = signal(true);
  protected readonly editing = signal(false);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.projectService.get(id).subscribe((project) => {
      this.project.set(project);
      this.loading.set(false);
    });
  }

  canManage(): boolean {
    const project = this.project();
    const user = this.auth.currentUser();
    if (!project || !user) {
      return false;
    }
    return user.role === 'ADMIN' || user.id === project.submitter.id;
  }

  onSaved(): void {
    this.editing.set(false);
    this.load();
  }

  onVoted(result: VoteResult): void {
    const project = this.project();
    if (project) {
      this.project.set({ ...project, ...result });
    }
  }

  delete(): void {
    const project = this.project();
    if (!project || !confirm('Delete this project submission?')) {
      return;
    }
    this.projectService.delete(project.id).subscribe(() => this.router.navigateByUrl(`/assignments/${project.assignmentId}`));
  }
}
