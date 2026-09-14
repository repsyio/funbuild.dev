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

  get(id: number): Observable<Assignment> {
    return this.http.get<Assignment>(`${this.base}/${id}`);
  }

  create(req: AssignmentRequest): Observable<Assignment> {
    return this.http.post<Assignment>(this.base, req);
  }

  update(id: number, req: AssignmentRequest): Observable<Assignment> {
    return this.http.put<Assignment>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
