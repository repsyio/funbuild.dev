import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectForm } from '../../components/project-form/project-form';
import { Project } from '../../core/models';
import { ProjectService } from '../../core/project.service';

@Component({
  selector: 'app-admin-projects',
  imports: [RouterLink, DatePipe, ProjectForm],
  templateUrl: './admin-projects.html',
})
export class AdminProjects implements OnInit {
  private readonly projectService = inject(ProjectService);

  protected readonly projects = signal<Project[]>([]);
  protected readonly loading = signal(true);
  protected readonly editingId = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.projectService.list().subscribe((projects) => {
      this.projects.set(projects);
      this.loading.set(false);
    });
  }

  onSaved(): void {
    this.editingId.set(null);
    this.load();
  }

  delete(project: Project): void {
    if (!confirm(`Delete "${project.title}"?`)) {
      return;
    }
    this.projectService.delete(project.id).subscribe(() => this.load());
  }
}
