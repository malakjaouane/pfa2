import * as React from 'react';
import { cn } from '../../lib/utils';

/**
 * Skeleton d'affichage pendant le chargement (Phase 3).
 *
 * Affiche un placeholder animé qui respecte prefers-reduced-motion.
 * Usage : <Skeleton className="h-8 w-48" />
 */
const Skeleton = React.forwardRef(({ className, ...props }, ref) => {
  return (
    <span
      ref={ref}
      className={cn(
        'flex -space-x-px overflow-hidden rounded-md bg-muted text-muted-foreground animate-pulse',
        className
      )}
      {...props}
    />
  );
});
Skeleton.displayName = 'Skeleton';

export { Skeleton };
