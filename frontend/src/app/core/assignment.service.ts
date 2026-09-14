import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Assignment, AssignmentRequest } from './models';

@Injectable({ providedIn: 'root' })
export class AssignmentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/assignments`;

  list(status?: 'active'): Observable<Assignment[]> {
    return this.http.get<Assignment[]>(this.base, status ? { params: { status } } : {});
  }

  get(slug: string): Observable<Assignment> {
    return this.http.get<Assignment>(`${this.base}/${slug}`);
  }

  create(req: AssignmentRequest): Observable<Assignment> {
    return this.http.post<Assignment>(this.base, req);
  }

  update(slug: string, req: AssignmentRequest): Observable<Assignment> {
    return this.http.put<Assignment>(`${this.base}/${slug}`, req);
  }

  delete(slug: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${slug}`);
  }
}
