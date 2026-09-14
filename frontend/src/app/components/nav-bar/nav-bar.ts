import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { initials } from '../../core/initials';

@Component({
  selector: 'app-nav-bar',
  imports: [RouterLink],
  templateUrl: './nav-bar.html',
})
export class NavBar {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);

  protected readonly adminMenuOpen = signal(false);
  protected readonly initials = initials;

  toggleAdminMenu(): void {
    this.adminMenuOpen.update((open) => !open);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.adminMenuOpen() && !this.elementRef.nativeElement.contains(event.target)) {
      this.adminMenuOpen.set(false);
    }
  }

  logout(): void {
    this.adminMenuOpen.set(false);
    this.auth.logout();
    this.router.navigateByUrl('/');
  }
}
