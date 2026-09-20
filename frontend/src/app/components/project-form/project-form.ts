import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Project } from '../../core/models';
import { ProjectService } from '../../core/project.service';
import { TechLabelSelect } from '../tech-label-select/tech-label-select';

@Component({
  selector: 'app-project-form',
  imports: [FormsModule, TechLabelSelect],
  templateUrl: './project-form.html',
})
export class ProjectForm implements OnInit {
  private readonly projectService = inject(ProjectService);

  /** Required when creating a new submission; ignored when editing (the assignment is fixed). */
  readonly assignmentId = input<string>();
  readonly existingProject = input<Project | null>(null);
  readonly saved = output<Project>();
  readonly cancelled = output<void>();

  protected title = '';
  protected description = '';
  protected showcaseUrl = '';
  protected gitRepoUrl = '';
  protected techLabels: string[] = [];

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const existing = this.existingProject();
    if (existing) {
      this.title = existing.title;
      this.description = existing.description;
      this.showcaseUrl = existing.showcaseUrl;
      this.gitRepoUrl = existing.gitRepoUrl ?? '';
      this.techLabels = existing.techLabels.map((l) => l.name);
    }
  }

  submit(): void {
    const existing = this.existingProject();
    const req = {
      assignmentId: existing ? existing.assignmentId : this.assignmentId()!,
      title: this.title,
      description: this.description,
      showcaseUrl: this.showcaseUrl,
      gitRepoUrl: this.gitRepoUrl.trim() ? this.gitRepoUrl.trim() : null,
      techLabels: this.techLabels,
    };

    this.saving.set(true);
    this.error.set(null);
    const request = existing
      ? this.projectService.update(existing.id, req)
      : this.projectService.submit(req);

    request.subscribe({
      next: (project) => {
        this.saving.set(false);
        this.saved.emit(project);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.text ?? err?.error?.message ?? 'Could not save the project. Please try again.');
      },
    });
  }
}
