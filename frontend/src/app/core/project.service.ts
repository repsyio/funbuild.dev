import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Project, ProjectRequest, VoteResult } from './models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/projects`;

  list(assignmentId?: string): Observable<Project[]> {
    return this.http.get<Project[]>(this.base, assignmentId ? { params: { assignmentId } } : {});
  }

  top(limit = 10): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.base}/top`, { params: { limit } });
  }

  get(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.base}/${id}`);
  }

  submit(req: ProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.base, req);
  }

  update(id: string, req: ProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${this.base}/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  vote(id: string): Observable<VoteResult> {
    return this.http.post<VoteResult>(`${this.base}/${id}/vote`, {});
  }
}
