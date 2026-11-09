import { createContext, useCallback, useContext, useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ToastVariant = 'default' | 'destructive' | 'success' | 'info';

export interface ToastProps {
  id: string;
  title?: string;
  description?: string;
  variant?: ToastVariant;
  action?: React.ReactNode;
  duration?: number;
}

const variants: Record<ToastVariant, string> = {
  default: 'bg-background text-foreground border border-input',
  destructive: 'border-destructive/50 text-destructive dark:bg-destructive dark:text-destructive-foreground',
  success: 'bg-green-50 text-green-900 border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800/50',
  info: 'bg-blue-50 text-blue-900 border-blue-200 dark:bg-blue-900/20 dark:text-blue-400 dark:border-blue-800/50',
};

const ToastContext = createContext<{
  toasts: ToastProps[];
  toast: (props: Omit<ToastProps, 'id'>) => string;
  dismiss: (id: string) => void;
} | null>(null);

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within a ToastProvider');
  return context;
}

let id = 0;
function genId() {
  id += 1;
  return id.toString();
}

const TOAST_LIMIT = 5;

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastProps[]>([]);

  const toast = useCallback(
    ({
      title,
      description,
      variant = 'default',
      action,
      duration = 4000,
    }: Omit<ToastProps, 'id'>) => {
      const newId = genId();
      const newToast: ToastProps = {
        id: newId,
        title,
        description,
        variant,
        action,
        duration,
      };
      setToasts((ts) => [newToast, ...ts].slice(0, TOAST_LIMIT));
      if (duration > 0) {
        setTimeout(() => {
          setToasts((ts) => ts.filter((t) => t.id !== newId));
        }, duration);
      }
      return newId;
    },
    []
  );

  const dismiss = useCallback((id: string) => {
    setToasts((ts) => ts.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, toast, dismiss }}>
      {children}
    </ToastContext.Provider>
  );
}

export function Toast({ id, title, description, variant = 'default', action }: ToastProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -10, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -10, scale: 0.95, transition: { duration: 0.2 } }}
      className={cn(
        'relative w-full overflow-hidden border px-4 py-3 text-sm shadow-lg',
        variants[variant]
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          {variant === 'success' && (
            <svg className="h-5 w-5 text-green-500" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
          )}
          {variant === 'destructive' && (
            <svg className="h-5 w-5 text-red-500" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 01-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
            </svg>
          )}
          <div className="text-sm font-medium">{title}</div>
          {description && <div className="text-sm opacity-80">{description}</div>}
        </div>
        {action && (
          <button className="inline-flex items-center justify-center rounded-md bg-transparent px-2 py-1.5 text-sm font-medium ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2">
            {action}
          </button>
        )}
        <button
          onClick={() => {}}
          className="absolute right-2 top-2 rounded-md p-1 text-muted-foreground hover:bg-accent hover:text-accent-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
        >
          <X size={12} />
        </button>
      </div>
    </motion.div>
  );
}

export function Toaster({ className }: { className?: string }) {
  const { toasts } = useContext(ToastContext);
  return (
    <div
      className={cn(
        'fixed top-0 z-[50] flex max-h-screen w-full flex-col justify-center sm:right-0 sm:top-0 sm:left-0 sm:items-center',
        className
      )}
    >
      <AnimatePresence>
        {toasts.map((toast) => (
          <Toast key={toast.id} {...toast} />
        ))}
      </AnimatePresence>
    </div>
  );
}
