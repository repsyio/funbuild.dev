import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AssignmentService } from '../../core/assignment.service';
import { Assignment } from '../../core/models';

interface FormState {
  title: string;
  description: string;
  startAt: string;
  endAt: string;
}

const EMPTY_FORM: FormState = { title: '', description: '', startAt: '', endAt: '' };

@Component({
  selector: 'app-admin-assignments',
  imports: [DatePipe, FormsModule],
  templateUrl: './admin-assignments.html',
})
export class AdminAssignments implements OnInit {
  private readonly assignmentService = inject(AssignmentService);

  protected readonly assignments = signal<Assignment[]>([]);
  protected readonly loading = signal(true);
  protected readonly editingId = signal<string | 'new' | null>(null);
  protected readonly error = signal<string | null>(null);
  protected form: FormState = { ...EMPTY_FORM };

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.assignmentService.list().subscribe((assignments) => {
      this.assignments.set(assignments);
      this.loading.set(false);
    });
  }

  startCreate(): void {
    this.form = { ...EMPTY_FORM };
    this.error.set(null);
    this.editingId.set('new');
  }

  startEdit(assignment: Assignment): void {
    this.form = {
      title: assignment.title,
      description: assignment.description,
      startAt: toLocalInputValue(assignment.startAt),
      endAt: toLocalInputValue(assignment.endAt),
    };
    this.error.set(null);
    this.editingId.set(assignment.slug);
  }

  cancel(): void {
    this.editingId.set(null);
  }

  save(): void {
    const req = {
      title: this.form.title,
      description: this.form.description,
      startAt: new Date(this.form.startAt).toISOString(),
      endAt: new Date(this.form.endAt).toISOString(),
    };
    const id = this.editingId();
    const request = id === 'new' ? this.assignmentService.create(req) : this.assignmentService.update(id!, req);
    request.subscribe({
      next: () => {
        this.editingId.set(null);
        this.load();
      },
      error: (err) => this.error.set(err?.error?.message ?? 'Could not save the assignment.'),
    });
  }

  delete(assignment: Assignment): void {
    if (!confirm(`Delete "${assignment.title}"? This also removes its submissions.`)) {
      return;
    }
    this.assignmentService.delete(assignment.slug).subscribe(() => this.load());
  }
}

function toLocalInputValue(iso: string): string {
  const date = new Date(iso);
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}
