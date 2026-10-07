import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Pager } from './pager';

function setup(page: number, totalPages: number) {
  const fixture = TestBed.createComponent(Pager);
  fixture.componentRef.setInput('page', page);
  fixture.componentRef.setInput('totalPages', totalPages);
  fixture.detectChanges();
  const emitted: number[] = [];
  fixture.componentInstance.pageChange.subscribe((p) => emitted.push(p));
  const buttons = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
  return { fixture, emitted, buttons };
}

describe('Pager', () => {
  it('no se muestra con una sola página', () => {
    const { buttons } = setup(0, 1);
    expect(buttons().length).toBe(0);
  });

  it('deshabilita "Anterior" en la primera página', () => {
    const { buttons } = setup(0, 3);
    expect(buttons()[0].disabled).toBe(true);
    expect(buttons()[1].disabled).toBe(false);
  });

  it('deshabilita "Siguiente" en la última página', () => {
    const { buttons } = setup(2, 3);
    expect(buttons()[1].disabled).toBe(true);
  });

  it('emite la página destino al navegar', () => {
    const { buttons, emitted } = setup(1, 3);
    buttons()[1].click();
    buttons()[0].click();
    expect(emitted).toEqual([2, 0]);
  });

  it('go ignora páginas fuera de rango', () => {
    const { fixture, emitted } = setup(0, 2);
    fixture.componentInstance.go(-1);
    fixture.componentInstance.go(2);
    expect(emitted).toEqual([]);
  });
});
