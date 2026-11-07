import { ChangeDetectionStrategy, Component, input, output, TemplateRef, viewChild } from '@angular/core';
import { CdkMenuModule } from '@angular/cdk/menu';

export interface ContextMenuItem {
  readonly id: string;
  readonly label: string;
  readonly disabled?: boolean;
  readonly children?: readonly ContextMenuItem[];
}
export interface ContextMenuAction { readonly id: string; readonly x: number; readonly y: number; }

/** Items remain live inputs; callers own actions and any subsequent popup. */
@Component({
  selector: 'vc-context-menu',
  exportAs: 'vcContextMenu',
  imports: [CdkMenuModule],
  templateUrl: './context-menu.component.html',
  styleUrl: './context-menu.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextMenuComponent {
  readonly menu = viewChild.required<TemplateRef<unknown>>('root');
  readonly items = input.required<readonly ContextMenuItem[]>();
  readonly action = output<ContextMenuAction>();
  private point = { x: 0, y: 0 };
  childrenFor(path: readonly string[]): readonly ContextMenuItem[] {
    let items = this.items();
    for (const id of path) items = items.find(item => item.id === id)?.children ?? [];
    return items;
  }
  remember(event: MouseEvent): void { this.point = { x: event.clientX, y: event.clientY }; }
  choose(item: ContextMenuItem): void {
    if (!item.disabled && !item.children?.length) this.action.emit({ id: item.id, ...this.point });
  }
}
