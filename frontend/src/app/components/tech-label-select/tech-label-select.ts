import { Component, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TechLabel } from '../../core/models';
import { TechLabelService } from '../../core/tech-label.service';

/** Creatable multi-select: pick an existing tech label or type a new one to add it. */
@Component({
  selector: 'app-tech-label-select',
  imports: [FormsModule],
  templateUrl: './tech-label-select.html',
})
export class TechLabelSelect {
  private readonly techLabelService = inject(TechLabelService);

  readonly labels = model<string[]>([]);

  protected query = '';
  protected readonly suggestions = signal<TechLabel[]>([]);
  protected readonly open = signal(false);

  onQueryChange(): void {
    const q = this.query.trim();
    if (!q) {
      this.suggestions.set([]);
      return;
    }
    this.techLabelService.search(q).subscribe((results) => this.suggestions.set(results));
  }

  addFromSuggestion(label: TechLabel): void {
    this.add(label.name);
  }

  addFromQuery(): void {
    if (this.query.trim()) {
      this.add(this.query);
    }
  }

  remove(name: string): void {
    this.labels.set(this.labels().filter((l) => l !== name));
  }

  private add(name: string): void {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    const exists = this.labels().some((l) => l.toLowerCase() === trimmed.toLowerCase());
    if (!exists) {
      this.labels.set([...this.labels(), trimmed]);
    }
    this.query = '';
    this.suggestions.set([]);
    this.open.set(false);
  }
}
