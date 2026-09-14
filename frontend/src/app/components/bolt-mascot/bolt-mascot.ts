import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-bolt-mascot',
  templateUrl: './bolt-mascot.html',
})
export class BoltMascot {
  readonly width = input<number>(150);
  protected readonly height = computed(() => Math.round((this.width() * 158) / 150));
}
