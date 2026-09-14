import { Component, input } from '@angular/core';

@Component({
  selector: 'app-brand-mark',
  templateUrl: './brand-mark.html',
})
export class BrandMark {
  readonly size = input<number>(26);
}
