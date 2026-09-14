import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TechLabel } from './models';

@Injectable({ providedIn: 'root' })
export class TechLabelService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/tech-labels`;

  search(query?: string): Observable<TechLabel[]> {
    return this.http.get<TechLabel[]>(this.base, query ? { params: { q: query } } : {});
  }
}
