import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProductService } from '../../core/product.service';
import { ToastService } from '../../core/toast.service';
import { Product, ProductRequest } from '../../core/models';
import { Pager } from '../../shared/pager/pager';

@Component({
  selector: 'app-admin',
  imports: [ReactiveFormsModule, CurrencyPipe, Pager],
  templateUrl: './admin.html',
  styleUrl: './admin.css',
})
export class Admin {
  private fb = inject(FormBuilder);
  private service = inject(ProductService);
  private toasts = inject(ToastService);

  products = signal<Product[]>([]);
  page = signal(0);
  totalPages = signal(0);
  loading = signal(true);
  saving = signal(false);
  submitted = signal(false);
  /** Producto en edición; null = formulario de alta. */
  editing = signal<Product | null>(null);
  deleting = signal<number | null>(null);

  // Mismos límites que ProductRequest del backend
  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    price: [0, [Validators.required, Validators.min(0)]],
    stock: [0, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]],
  });

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.service.list(page).subscribe({
      next: (res) => {
        this.products.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toasts.error('No se pudieron cargar los productos');
      },
    });
  }

  showError(name: 'name' | 'price' | 'stock'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  edit(product: Product): void {
    this.editing.set(product);
    this.submitted.set(false);
    this.form.reset({
      name: product.name,
      description: product.description ?? '',
      price: product.price,
      stock: product.stock,
    });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.submitted.set(false);
    this.form.reset({ name: '', description: '', price: 0, stock: 0 });
  }

  save(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    const request: ProductRequest = {
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      price: Number(raw.price),
      stock: Number(raw.stock),
    };
    const current = this.editing();
    this.saving.set(true);
    const call = current ? this.service.update(current.id, request) : this.service.create(request);
    call.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.toasts.success(current ? `"${saved.name}" actualizado` : `"${saved.name}" creado`);
        this.cancelEdit();
        this.load(this.page());
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.toasts.error(
          err.status === 403 ? 'No tienes permisos para esta acción' : 'No se pudo guardar el producto',
        );
      },
    });
  }

  askDelete(id: number): void {
    this.deleting.set(id);
  }

  cancelDelete(): void {
    this.deleting.set(null);
  }

  confirmDelete(product: Product): void {
    this.deleting.set(null);
    this.service.delete(product.id).subscribe({
      next: () => {
        this.toasts.success(`"${product.name}" eliminado`);
        // si era el único de la última página, retrocede una
        const target = this.products().length === 1 && this.page() > 0 ? this.page() - 1 : this.page();
        if (this.editing()?.id === product.id) {
          this.cancelEdit();
        }
        this.load(target);
      },
      error: () =>
        this.toasts.error(`No se pudo eliminar "${product.name}". Puede tener pedidos asociados.`),
    });
  }
}
