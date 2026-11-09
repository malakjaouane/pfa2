import * as React from 'react';
import { cn } from '@/lib/utils';

const Separator = ({ className, ...props }) => (
  <div
    className={cn('w-full bg-border shrink-0', className)}
    {...props}
  />
);

export { Separator };
