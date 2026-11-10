import * as React from 'react';
import { cn } from '../../lib/utils';

/**
 * État vide réutilisable (Phase 3).
 *
 * Affiche un message clair quand aucune donnée n'est disponible.
 * Usage : <EmptyState icon={<IconUsers />} titre="…" description="…" action={<Button>…</Button>} />
 */
export function EmptyState({ icon, titre, description, action, className }) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center py-12 text-center',
        className
      )}
    >
      {icon && <div className="mb-4 opacity-50">{icon}</div>}
      <h2 className="text-lg font-semibold text-foreground">{titre}</h2>
      {description && (
        <p className="mt-2 text-sm text-muted-foreground">{description}</p>
      )}
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
