import { Routes } from '@angular/router';
import { adminGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
  },
  {
    path: 'assignments/:id',
    loadComponent: () => import('./pages/assignment-detail/assignment-detail').then((m) => m.AssignmentDetail),
  },
  {
    path: 'projects/:id',
    loadComponent: () => import('./pages/project-detail/project-detail').then((m) => m.ProjectDetail),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register').then((m) => m.Register),
  },
  {
    path: 'oauth-callback',
    loadComponent: () => import('./pages/oauth-callback/oauth-callback').then((m) => m.OauthCallback),
  },
  {
    path: 'admin/users',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/admin-users/admin-users').then((m) => m.AdminUsers),
  },
  {
    path: 'admin/assignments',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/admin-assignments/admin-assignments').then((m) => m.AdminAssignments),
  },
  {
    path: 'admin/projects',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/admin-projects/admin-projects').then((m) => m.AdminProjects),
  },
  { path: '**', redirectTo: '' },
];
