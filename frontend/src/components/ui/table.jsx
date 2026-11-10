import * as React from 'react';
import { forwardRef } from 'react';
import { cn } from '../../lib/utils';

/**
 * Tableau de données (Phase 3).
 *
 * Reprend le rendu du `.table` legacy de styles.css (en-têtes capitales
 * sur fond muted, lignes séparées, survol) en classes Tailwind, pour que
 * les pages migrées n'aient plus besoin des styles natifs.
 *
 * Usage :
 * <Table>
 *   <TableHeader><TableRow><TableHead>Col</TableHead></TableRow></TableHeader>
 *   <TableBody><TableRow><TableCell>Val</TableCell></TableRow></TableBody>
 * </Table>
 */
const Table = forwardRef(({ className, ...props }, ref) => (
  <div className="relative w-full overflow-auto">
    <table
      ref={ref}
      className={cn('w-full caption-bottom border-collapse text-[13px]', className)}
      {...props}
    />
  </div>
));
Table.displayName = 'Table';

const TableHeader = forwardRef(({ className, ...props }, ref) => (
  <thead ref={ref} className={cn('[&_tr]:border-b', className)} {...props} />
));
TableHeader.displayName = 'TableHeader';

const TableBody = forwardRef(({ className, ...props }, ref) => (
  <tbody ref={ref} className={cn('[&_tr:last-child]:border-0', className)} {...props} />
));
TableBody.displayName = 'TableBody';

const TableFooter = forwardRef(({ className, ...props }, ref) => (
  <tfoot
    ref={ref}
    className={cn('border-t bg-muted/50 font-medium [&>tr]:last:border-b-0', className)}
    {...props}
  />
));
TableFooter.displayName = 'TableFooter';

const TableRow = forwardRef(({ className, ...props }, ref) => (
  <tr
    ref={ref}
    className={cn(
      'border-b border-border transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted',
      className
    )}
    {...props}
  />
));
TableRow.displayName = 'TableRow';

const TableHead = forwardRef(({ className, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      'h-10 whitespace-nowrap px-3 py-2.5 text-left align-middle text-[11px] font-semibold uppercase tracking-[0.04em] text-muted-foreground bg-muted border-b border-border first:rounded-tl-lg last:rounded-tr-lg',
      className
    )}
    {...props}
  />
));
TableHead.displayName = 'TableHead';

const TableCell = forwardRef(({ className, ...props }, ref) => (
  <td
    ref={ref}
    className={cn('px-3 py-2.5 align-middle border-b border-border', className)}
    {...props}
  />
));
TableCell.displayName = 'TableCell';

const TableCaption = forwardRef(({ className, ...props }, ref) => (
  <caption
    ref={ref}
    className={cn('mt-4 text-sm text-muted-foreground', className)}
    {...props}
  />
));
TableCaption.displayName = 'TableCaption';

export {
  Table,
  TableHeader,
  TableBody,
  TableFooter,
  TableHead,
  TableRow,
  TableCell,
  TableCaption,
};
