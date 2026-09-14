import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Role, UserSummary } from './models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/users`;

  list(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(this.base);
  }

  updateMe(displayName: string): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${this.base}/me`, { displayName });
  }

  updateRole(id: string, role: Role): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${this.base}/${id}`, { role });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
