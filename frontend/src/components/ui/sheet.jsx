import * as React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

const Sheet = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement> & {
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
    side?: 'top' | 'right' | 'bottom' | 'left';
  }
>(({ className, open = true, onOpenChange, side = 'right', children, ...props }, ref) => {
  const handleClose = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onOpenChange?.(false);
    }
  };

  const sideStyles: Record<string, string> = {
    top: 'top-0 left-0 w-full h-auto rounded-none border-b',
    right: 'top-0 right-0 h-full w-auto rounded-none border-l',
    bottom: 'bottom-0 left-0 w-full h-auto rounded-none border-t',
    left: 'top-0 left-0 h-full w-auto rounded-none border-r',
  };

  return (
    <>
      <AnimatePresence mode="wait">
        {open && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleClose}
            className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm transition-opacity"
            aria-hidden="true"
          />
        )}
        <motion.div
          initial={{ opacity: 0, y: side === 'top' ? -20 : side === 'bottom' ? 20 : 0, x: side === 'left' ? -20 : side === 'right' ? 20 : 0 }}
          animate={{ opacity: 1, y: 0, x: 0 }}
          exit={{ opacity: 0, y: side === 'top' ? -20 : side === 'bottom' ? 20 : 0, x: side === 'left' ? -20 : side === 'right' ? 20 : 0 }}
          ref={ref}
          onClick={(e) => e.stopPropagation()}
          className={cn(
            'fixed z-50 flex flex-col gap-1 bg-background border shadow-lg',
            sideStyles[side],
            className
          )}
          {...props}
        >
          {children}
        </motion.div>
      </AnimatePresence>
    </>
  );
});
Sheet.displayName = 'Sheet';

const SheetTrigger = React.forwardRef<
  HTMLButtonElement,
  React.HTMLAttributes<HTMLButtonElement>
>(({ className, ...props }, ref) => (
  <button
    ref={ref}
    className={cn(
      'inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',
      className
    )}
    {...props}
  />
));
SheetTrigger.displayName = 'SheetTrigger';

const SheetContent = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement> & {
    header?: React.ReactNode;
    footer?: React.ReactNode;
  }
>(({ className, header, footer, ...props }, ref) => (
  <motion.div
    ref={ref}
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: 10 }}
    className={cn('flex-1 overflow-y-auto p-6', className)}
    {...props}
  >
    {header}
    {footer && <Separator>{footer}</Separator>}
  </motion.div>
));
SheetContent.displayName = 'SheetContent';

const SheetHeader = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
  <div
    ref={ref}
    className={cn('flex flex-col space-y-1.5 px-6 pt-6', className)}
    {...props}
  />
));
SheetHeader.displayName = 'SheetHeader';

const SheetFooter = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
  <div
    ref={ref}
    className={cn('flex flex-col-reverse space-y-1.5 px-6 pb-6', className)}
    {...props}
  />
));
SheetFooter.displayName = 'SheetFooter';

const SheetTitle = React.forwardRef<
  HTMLHeadingElement,
  React.HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h3
    ref={ref}
    className={cn('text-xl font-semibold leading-none tracking-tight', className)}
    {...props}
  />
));
SheetTitle.displayName = 'SheetTitle';

const SheetDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p
    ref={ref}
    className={cn('text-sm text-muted-foreground', className)}
    {...props}
  />
));
SheetDescription.displayName = 'SheetDescription';

const SheetClose = React.forwardRef<
  HTMLButtonElement,
  React.HTMLAttributes<HTMLButtonElement>
>(({ className, ...props }, ref) => (
  <button
    ref={ref}
    className={cn(
      'absolute right-4 top-4 rounded-sm p-1.5 text-muted-foreground ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:opacity-50',
      className
    )}
    {...props}
  >
    <X size={18} />
    <span className="sr-only">Fermer</span>
  </button>
));
SheetClose.displayName = 'SheetClose';

const Separator = ({ className, ...props }: React.HTMLAttributes<HTMLHRElement>) => (
  <div className={cn('w-full bg-border shrink-0', className)} {...props} />
);

export {
  Sheet,
  SheetTrigger,
  SheetContent,
  SheetHeader,
  SheetFooter,
  SheetTitle,
  SheetDescription,
  SheetClose,
  Separator,
};
