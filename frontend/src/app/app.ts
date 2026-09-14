import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavBar } from './components/nav-bar/nav-bar';

@Component({
  imports: [RouterOutlet, NavBar],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {}
