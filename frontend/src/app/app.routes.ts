import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/statements/statements').then((m) => m.Statements),
  },
  {
    path: 'statements/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/statement/statement').then((m) => m.StatementPage),
  },
  { path: '**', redirectTo: '' },
];
